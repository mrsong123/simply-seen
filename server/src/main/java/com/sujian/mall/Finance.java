package com.sujian.mall;

import static com.sujian.mall.Db.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Finance {
  final Db d;
  final Settings c;
  final Commerce shop;
  final Wechat w;

  public Finance(Db d, Settings c, Commerce shop, Wechat w) {
    this.d = d;
    this.c = c;
    this.shop = shop;
    this.w = w;
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public Object refund(long uid, String key, Map<String, Object> b) {
    return shop.idem(
        uid,
        "refund",
        key,
        b,
        () -> {
          String no = s(b, "orderNo");
          var o = d.one("SELECT * FROM shop_order WHERE id=? AND member_id=? FOR UPDATE", no, uid);
          Problem.require(
              s(o, "payment_state").equals("SUCCESS") && !s(o, "state").equals("REFUNDED"),
              "ORDER_STATE",
              "订单不满足退款条件");
          Problem.require(
              d.count(
                      "SELECT COUNT(*) FROM refund WHERE order_id=? AND state IN"
                          + " ('REQUESTED','REVIEWED','PROCESSING','ABNORMAL')",
                      no)
                  == 0,
              "REFUND_PENDING",
              "已有进行中的售后，请等待处理");
          String reason = s(b, "reason").trim();
          Problem.require(
              reason.length() >= 2 && reason.length() <= 240, "INVALID_INPUT", "退款原因需为2至240字");
          var requested = (List<Map<String, Object>>) b.get("lines");
          Problem.require(requested != null && !requested.isEmpty(), "INVALID_INPUT", "请选择退款商品");
          Set<Long> seen = new HashSet<>();
          var lines = new ArrayList<Map<String, Object>>();
          long cash = 0, points = 0, qty = 0;
          for (var r : requested) {
            long id = n(r, "orderItemId"), q = n(r, "quantity");
            Problem.require(seen.add(id) && q > 0, "INVALID_QUANTITY", "退款商品重复或数量无效");
            var i = d.one("SELECT * FROM order_item WHERE id=? AND order_id=?", id, no);
            long available = n(i, "quantity") - n(i, "refunded_quantity");
            Problem.require(q <= available, "REFUND_AMOUNT_EXCEEDED", "退款数量超过剩余可退数量");
            long rc =
                q == available
                    ? n(i, "cash_amount") - n(i, "refunded_cash")
                    : n(i, "cash_amount") * q / n(i, "quantity");
            long rp =
                q == available
                    ? n(i, "points_amount") - n(i, "refunded_points")
                    : n(i, "points_amount") * q / n(i, "quantity");
            lines.add(Map.of("id", id, "quantity", q, "cash", rc, "points", rp));
            cash += rc;
            points += rp;
            qty += q;
          }
          long remain =
              d.count(
                  "SELECT SUM(quantity-refunded_quantity) FROM order_item WHERE order_id=?", no);
          if (qty == remain) cash += n(o, "freight");
          Problem.require(
              cash + n(o, "cash_refunded") <= n(o, "cash_due")
                  && points + n(o, "points_refunded") <= n(o, "points_used"),
              "REFUND_AMOUNT_EXCEEDED",
              "累计退款超过原始实付");
          String id = d.no("RF");
          d.exec(
              "INSERT INTO"
                  + " refund(id,order_id,member_id,state,amount,points,line_snapshot,reason,creator,created_at)"
                  + " VALUES(?,?,?,'REQUESTED',?,?,?,?,?,?)",
              id,
              no,
              uid,
              cash,
              points,
              d.encode(lines),
              reason,
              uid,
              d.now());
          return Map.of("id", id, "state", "REQUESTED");
        });
  }

  @Transactional
  public void reviewRefund(long actor, String id, boolean approve, String reason) {
    var r = d.one("SELECT * FROM refund WHERE id=? FOR UPDATE", id);
    Problem.require(s(r, "state").equals("REQUESTED"), "REFUND_STATE", "退款单已处理");
    if (!approve) {
      Problem.require(!reason.isBlank(), "REASON_REQUIRED", "请填写驳回原因");
      d.exec("UPDATE refund SET state='CLOSED',reviewer=?,reason=? WHERE id=?", actor, reason, id);
    } else {
      d.exec("UPDATE refund SET state='REVIEWED',reviewer=? WHERE id=?", actor, id);
      d.event(id + ":refund", "REFUND", id);
    }
    d.audit(actor, approve ? "REFUND_APPROVE" : "REFUND_REJECT", id, Map.of("reason", reason));
  }

  @Transactional
  public void refundSuccess(String id, String channelId) {
    var r = d.one("SELECT * FROM refund WHERE id=? FOR UPDATE", id);
    if (s(r, "state").equals("SUCCESS")) return;
    Problem.require(
        List.of("REVIEWED", "PROCESSING", "ABNORMAL").contains(s(r, "state")),
        "REFUND_STATE",
        "退款单尚未审核");
    String no = s(r, "order_id");
    var o = d.one("SELECT * FROM shop_order WHERE id=? FOR UPDATE", no);
    shop.benefits(no);
    long uid = n(r, "member_id");
    d.one("SELECT id FROM member WHERE id=? FOR UPDATE", uid);
    for (var line : d.list(s(r, "line_snapshot"))) {
      d.exec(
          "UPDATE order_item SET"
              + " refunded_quantity=refunded_quantity+?,refunded_cash=refunded_cash+?,refunded_points=refunded_points+?"
              + " WHERE id=?",
          n(line, "quantity"),
          n(line, "cash"),
          n(line, "points"),
          n(line, "id"));
      if (s(o, "state").equals("PAID"))
        d.exec(
            "UPDATE sku SET stock=stock+?,version=version+1 WHERE id=(SELECT sku_id FROM order_item"
                + " WHERE id=?)",
            n(line, "quantity"),
            n(line, "id"));
    }
    long refundCash = n(o, "cash_refunded") + n(r, "amount"),
        refundPoints = n(o, "points_refunded") + n(r, "points");
    Problem.require(
        refundCash <= n(o, "cash_due") && refundPoints <= n(o, "points_used"),
        "REFUND_AMOUNT_EXCEEDED",
        "累计退款超限");
    if (n(r, "points") > 0)
      shop.asset(uid, "points", n(r, "points"), id + ":points-back", no, "售后退回抵扣积分");
    var rule = d.decode(s(o, "rule_snapshot"));
    long original = Math.max(0, n(o, "cash_due") - n(o, "freight"));
    long cashNet = Math.max(0, original - refundCash);
    long earned = original / 10000 * n(rule, "pointsPer100Yuan"),
        remaining = cashNet / 10000 * n(rule, "pointsPer100Yuan");
    long already =
        -d.count(
            "SELECT COALESCE(SUM(amount),0) FROM ledger WHERE member_id=? AND order_id=? AND"
                + " note='退款回收赠送积分'",
            uid,
            no);
    long revoke = Math.max(0, earned - remaining - already);
    if (revoke > 0) {
      var m = d.one("SELECT * FROM member WHERE id=? FOR UPDATE", uid);
      long take = Math.min(n(m, "points"), revoke);
      if (take > 0) shop.asset(uid, "points", -take, id + ":earned-revoke", no, "退款回收赠送积分");
      if (take < revoke)
        d.issue("POINTS_RECOVERY", id, "赠送积分不足回收，缺口 " + (revoke - take) + " 点；暂停该会员新佣金提现，待人工核销");
    }
    for (var comm :
        d.all("SELECT * FROM commission WHERE order_id=? ORDER BY beneficiary_id FOR UPDATE", no)) {
      long target = original == 0 ? 0 : n(comm, "original_amount") * cashNet / original,
          delta = n(comm, "amount") - target;
      long beneficiary = n(comm, "beneficiary_id");
      if (delta > 0 && s(comm, "state").equals("SETTLED")) {
        var m = d.one("SELECT * FROM member WHERE id=? FOR UPDATE", beneficiary);
        long take = Math.min(delta, n(m, "available"));
        if (take > 0)
          shop.asset(
              beneficiary,
              "available",
              -take,
              id + ":commission-revoke:" + beneficiary,
              no,
              "来源订单退款冲回佣金");
        if (take < delta) {
          shop.asset(
              beneficiary,
              "debt",
              delta - take,
              id + ":commission-debt:" + beneficiary,
              no,
              "已结佣金待追偿");
          d.issue(
              "COMMISSION_RECOVERY",
              id + ":" + beneficiary,
              "佣金已冻结或出款，待追偿 " + (delta - take) + " 分");
        }
      }
      d.exec(
          "UPDATE commission SET amount=?,state=? WHERE id=?",
          target,
          target == 0 ? "REVERSED" : s(comm, "state"),
          n(comm, "id"));
    }
    boolean full =
        d.count("SELECT SUM(quantity-refunded_quantity) FROM order_item WHERE order_id=?", no) == 0;
    d.exec(
        "UPDATE shop_order SET cash_refunded=?,points_refunded=?,state=? WHERE id=?",
        refundCash,
        refundPoints,
        full ? "REFUNDED" : s(o, "state"),
        no);
    d.exec("UPDATE refund SET state='SUCCESS',channel_id=? WHERE id=?", channelId, id);
    String month =
        Instant.ofEpochMilli(n(o, "paid_at"))
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toString()
            .substring(0, 7);
    for (var st : d.all("SELECT * FROM settlement WHERE period=?", month)) {
      if (s(st, "state").equals("DRAFT"))
        d.exec("UPDATE settlement SET state='INVALIDATED' WHERE id=?", n(st, "id"));
      else d.issue("MONTHLY_REFUND", id, "已审批活动发生退款，请复核历史结算 " + n(st, "id"));
    }
  }

  @Transactional
  public void settleCommissions() {
    long now = d.now();
    for (var row :
        d.all(
            "SELECT c.id FROM commission c JOIN shop_order o ON o.id=c.order_id WHERE"
                + " c.state='PENDING' AND o.state='COMPLETED' AND o.completed_at IS NOT NULL ORDER"
                + " BY c.id LIMIT 100")) {
      var comm = d.one("SELECT * FROM commission WHERE id=? FOR UPDATE", n(row, "id"));
      var o = d.one("SELECT * FROM shop_order WHERE id=?", s(comm, "order_id"));
      var rule = d.decode(s(comm, "rule_snapshot"));
      if (!s(comm, "state").equals("PENDING")
          || n(o, "completed_at") + n(rule, "settlementDays") * 86400000L > now) continue;
      if (d.count(
              "SELECT COUNT(*) FROM refund WHERE order_id=? AND state IN"
                  + " ('REQUESTED','REVIEWED','PROCESSING','ABNORMAL')",
              s(comm, "order_id"))
          > 0) continue;
      long uid = n(comm, "beneficiary_id");
      var m = d.one("SELECT * FROM member WHERE id=? FOR UPDATE", uid);
      if (b(m, "blocked")) continue;
      long debt = Math.min(n(m, "debt"), n(comm, "amount"));
      if (debt > 0)
        shop.asset(
            uid,
            "debt",
            -debt,
            "C" + n(comm, "id") + ":offset",
            s(comm, "order_id"),
            "新佣金抵偿历史退款负债");
      if (n(comm, "amount") > debt)
        shop.asset(
            uid,
            "available",
            n(comm, "amount") - debt,
            "C" + n(comm, "id") + ":settle",
            s(comm, "order_id"),
            "售后期结束，成交佣金结算");
      d.exec("UPDATE commission SET state='SETTLED',settled_at=? WHERE id=?", now, n(comm, "id"));
    }
  }

  @Transactional
  public Object withdraw(long uid, String key, Map<String, Object> b) {
    return shop.idem(
        uid,
        "withdraw",
        key,
        b,
        () -> {
          Problem.require(
              c.enabled("TRANSFER_ENABLED") || c.dev(), "TRANSFER_DISABLED", "提现服务尚未开放");
          long amount = n(b, "amountFen");
          var m = d.one("SELECT * FROM member WHERE id=? FOR UPDATE", uid);
          Problem.require(
              amount >= c.integer("WITHDRAW_MIN_FEN", 1000)
                  && amount <= Math.min(200000, c.integer("WITHDRAW_MAX_FEN", 200000))
                  && n(m, "available") >= amount
                  && n(m, "debt") == 0,
              "WITHDRAW_LIMIT",
              "余额不足、存在待追偿款项或金额超出提现范围");
          Problem.require(
              d.count(
                      "SELECT COUNT(*) FROM issue WHERE kind='POINTS_RECOVERY' AND state='OPEN' AND"
                          + " resource IN (SELECT id FROM refund WHERE member_id=?)",
                      uid)
                  == 0,
              "RISK_REVIEW",
              "存在未完成积分回收，请先联系客服处理");
          String id = d.no("WD");
          shop.asset(uid, "available", -amount, id + ":hold", null, "申请提现预留");
          shop.asset(uid, "reserved", amount, id + ":reserve", null, "待审核提现冻结");
          d.exec(
              "INSERT INTO withdrawal(id,member_id,gross,tax,net,state,created_at)"
                  + " VALUES(?,?,?,0,?,'REVIEWING',?)",
              id,
              uid,
              amount,
              amount,
              d.now());
          return Map.of("id", id, "state", "REVIEWING");
        });
  }

  @Transactional
  public void reviewWithdrawal(long actor, String id, Map<String, Object> body) {
    var r = d.one("SELECT * FROM withdrawal WHERE id=? FOR UPDATE", id);
    Problem.require(s(r, "state").equals("REVIEWING"), "WITHDRAW_STATE", "提现单已处理");
    boolean approve = b(body, "approve");
    String reason = s(body, "reason");
    Problem.require(
        reason.length() >= 2 && reason.length() <= 240, "REASON_REQUIRED", "请填写审核依据或驳回原因");
    if (!approve) {
      releaseWithdrawal(r, "REJECTED");
      d.exec("UPDATE withdrawal SET reviewer=?,reason=? WHERE id=?", actor, reason, id);
    } else {
      long tax = n(body, "taxFen");
      Problem.require(tax >= 0 && tax < n(r, "gross"), "INVALID_TAX", "税额必须小于税前金额");
      d.exec(
          "UPDATE withdrawal SET state='APPROVED',reviewer=?,tax=?,net=gross-?,reason=? WHERE id=?",
          actor,
          tax,
          tax,
          reason,
          id);
    }
    d.audit(actor, "WITHDRAW_REVIEW", id, body);
  }

  @Transactional
  public void executeWithdrawal(long actor, String id) {
    var r = d.one("SELECT * FROM withdrawal WHERE id=? FOR UPDATE", id);
    Problem.require(
        s(r, "state").equals("APPROVED") && n(r, "reviewer") != actor,
        "DUAL_REVIEW_REQUIRED",
        "需由另一名管理员执行已审批出款");
    var m = d.one("SELECT * FROM member WHERE id=? FOR UPDATE", n(r, "member_id"));
    Problem.require(n(m, "debt") == 0 && !b(m, "blocked"), "RISK_REVIEW", "账户需风险复核");
    d.exec("UPDATE withdrawal SET state='SUBMITTED',executor=? WHERE id=?", actor, id);
    d.event(id + ":transfer", "TRANSFER", id);
    d.audit(actor, "WITHDRAW_EXECUTE", id, Map.of());
  }

  private void releaseWithdrawal(Map<String, Object> r, String state) {
    long uid = n(r, "member_id"), amount = n(r, "gross");
    String id = s(r, "id");
    shop.asset(uid, "reserved", -amount, id + ":release-reserved", null, "提现未出款解除冻结");
    shop.asset(uid, "available", amount, id + ":release-available", null, "提现金额退回可用");
    d.exec("UPDATE withdrawal SET state=? WHERE id=?", state, id);
  }

  @Transactional
  public void transferResult(String id, Map<String, Object> result) {
    var r = d.one("SELECT * FROM withdrawal WHERE id=? FOR UPDATE", id);
    if (List.of("SUCCESS", "FAILED", "REJECTED").contains(s(r, "state"))) return;
    Problem.require(
        List.of("SUBMITTED", "UNKNOWN", "CONFIRMING").contains(s(r, "state")),
        "WITHDRAW_STATE",
        "尚未授权出款");
    String state = s(result, "state");
    if (state.equals("SUCCESS")) {
      shop.asset(
          n(r, "member_id"), "reserved", -n(r, "gross"), id + ":paid", null, "渠道确认佣金出款（含税款预留）");
      d.exec(
          "UPDATE withdrawal SET state='SUCCESS',channel_id=? WHERE id=?",
          s(result, "transfer_bill_no"),
          id);
    } else if (List.of("FAIL", "CANCELLED").contains(state)) releaseWithdrawal(r, "FAILED");
    else
      d.exec(
          "UPDATE withdrawal SET state=?,package_info=?,channel_id=? WHERE id=?",
          state.equals("WAIT_USER_CONFIRM") ? "CONFIRMING" : "UNKNOWN",
          s(result, "package_info"),
          s(result, "transfer_bill_no"),
          id);
  }

  @Transactional
  public Object monthly(long actor, String month) {
    Problem.require(c.enabled("MONTHLY_ENABLED") || c.dev(), "MARKETING_DISABLED", "月度活动未开放");
    YearMonth ym;
    try {
      ym = YearMonth.parse(month);
    } catch (Exception e) {
      throw new Problem(400, "INVALID_MONTH", "月份格式为YYYY-MM");
    }
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    Problem.require(ym.isBefore(YearMonth.now(zone)), "PERIOD_OPEN", "仅能结算已结束月份");
    var rule = shop.rules();
    d.one("SELECT id FROM rule_version WHERE id=? FOR UPDATE", n(rule, "version"));
    var existing = d.all("SELECT * FROM settlement WHERE period=? FOR UPDATE", month);
    long id;
    if (!existing.isEmpty()) {
      var e = existing.getFirst();
      Problem.require(s(e, "state").equals("INVALIDATED"), "SETTLEMENT_EXISTS", "本月结算已存在");
      id = n(e, "id");
      d.exec("DELETE FROM settlement_award WHERE settlement_id=?", id);
      d.exec("UPDATE settlement SET state='DRAFT',creator=?,reviewer=NULL WHERE id=?", actor, id);
    } else {
      id = d.id();
      d.exec("INSERT INTO settlement VALUES(?,?,'DRAFT',0,?,NULL,?)", id, month, actor, d.now());
    }
    long from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
    var rows =
        d.all(
            "SELECT member_id,SUM(cash_due-freight-cash_refunded) AS sales,MIN(paid_at) AS"
                + " first_paid FROM shop_order WHERE paid_at>=? AND paid_at<? AND"
                + " payment_state='SUCCESS' AND state<>'REFUNDED' AND"
                + " cash_due-freight-cash_refunded>0 GROUP BY member_id ORDER BY sales"
                + " DESC,first_paid,member_id",
            from,
            to);
    long total = rows.stream().mapToLong(x -> n(x, "sales")).sum();
    long budget = Math.min(total * n(rule, "monthlyPoolBps") / 10000, n(rule, "monthlyBudgetFen")),
        left = budget;
    int limit = (rows.size() + 9) / 10;
    for (int i = 0; i < limit && left > 0; i++) {
      var row = rows.get(i);
      long award = Math.min(Math.min(n(row, "sales"), n(rule, "monthlyCapFen")), left);
      d.exec(
          "INSERT INTO settlement_award VALUES(?,?,?,?,?,?)",
          d.id(),
          id,
          n(row, "member_id"),
          n(row, "sales"),
          award,
          i + 1);
      left -= award;
    }
    d.exec("UPDATE settlement SET budget=? WHERE id=?", budget, id);
    d.audit(actor, "MONTHLY_DRAFT", month, Map.of("budget", budget, "used", budget - left));
    return Map.of("id", id, "budget", budget);
  }

  @Transactional
  public void approveMonthly(long actor, long id) {
    var st = d.one("SELECT * FROM settlement WHERE id=? FOR UPDATE", id);
    Problem.require(
        s(st, "state").equals("DRAFT") && n(st, "creator") != actor,
        "DUAL_REVIEW_REQUIRED",
        "结算需由另一名财务人员复核");
    Problem.require(c.enabled("MONTHLY_ENABLED") || c.dev(), "MARKETING_DISABLED", "月度活动未开放");
    for (var a :
        d.all("SELECT * FROM settlement_award WHERE settlement_id=? ORDER BY member_id", id))
      shop.asset(
          n(a, "member_id"),
          "available",
          n(a, "amount"),
          "MONTHLY:" + n(a, "id"),
          null,
          "月度优惠权益经双人复核入账");
    d.exec("UPDATE settlement SET state='APPROVED',reviewer=? WHERE id=?", actor, id);
    d.audit(actor, "MONTHLY_APPROVE", "" + id, Map.of());
  }

  @Transactional
  public void saveRule(long actor, Map<String, Object> b) {
    long l1 = n(b, "level1Bps"), l2 = n(b, "level2Bps");
    Problem.require(
        l1 >= 0
            && l2 >= 0
            && l1 + l2 <= 1000
            && n(b, "marginSharePercent") >= 0
            && n(b, "marginSharePercent") <= 50
            && n(b, "settlementDays") >= 7
            && n(b, "settlementDays") <= 90
            && n(b, "pointsPer100Yuan") >= 0
            && n(b, "pointsPer100Yuan") <= 100
            && n(b, "freightFen") >= 0
            && n(b, "freeShippingFen") >= 0
            && n(b, "monthlyBudgetFen") >= 0
            && n(b, "monthlyCapFen") <= 50000
            && n(b, "monthlyCapFen") >= 0
            && n(b, "monthlyPoolBps") >= 0
            && n(b, "monthlyPoolBps") <= 1000
            && n(b, "commissionBudgetFen") >= 0,
        "INVALID_RULE",
        "规则超出预算或比例护栏：佣金合计≤10%，毛利分成≤50%，结算7至90天，月奖励每人≤500元");
    var clean = new LinkedHashMap<String, Object>();
    for (String k :
        List.of(
            "level1Bps",
            "level2Bps",
            "marginSharePercent",
            "settlementDays",
            "pointsPer100Yuan",
            "freightFen",
            "freeShippingFen",
            "monthlyBudgetFen",
            "monthlyCapFen",
            "monthlyPoolBps",
            "commissionBudgetFen")) clean.put(k, n(b, k));
    clean.put("distributionEnabled", b(b, "distributionEnabled"));
    d.exec(
        "INSERT INTO rule_version VALUES(?,'DRAFT',?,?,NULL,?)",
        d.id(),
        d.encode(clean),
        actor,
        d.now());
    d.audit(actor, "RULE_DRAFT", "RULE", clean);
  }

  @Transactional
  public void approveRule(long actor, long id) {
    d.one("SELECT scope_key FROM system_guard WHERE scope_key='RULE' FOR UPDATE");
    var rule = d.one("SELECT * FROM rule_version WHERE id=? FOR UPDATE", id);
    Problem.require(
        s(rule, "state").equals("DRAFT") && n(rule, "creator") != actor,
        "DUAL_REVIEW_REQUIRED",
        "规则需由另一名管理员复核");
    var config = d.decode(s(rule, "config"));
    Problem.require(
        !b(config, "distributionEnabled") || c.enabled("DISTRIBUTION_ENABLED") || c.dev(),
        "MARKETING_DISABLED",
        "环境未开启分销，请先完成业务审批");
    d.exec("UPDATE rule_version SET state='ARCHIVED' WHERE state='ACTIVE'");
    d.exec("UPDATE rule_version SET state='ACTIVE',reviewer=? WHERE id=?", actor, id);
    d.audit(actor, "RULE_APPROVE", "" + id, config);
  }
}
