package com.sujian.mall;

import static com.sujian.mall.Db.*;

import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Jobs {
  final Db d;
  final Settings c;
  final Commerce shop;
  final Finance finance;
  final Wechat w;
  final TransactionTemplate tx;

  public Jobs(Db d, Settings c, Commerce s, Finance f, Wechat w, PlatformTransactionManager tm) {
    this.d = d;
    this.c = c;
    shop = s;
    finance = f;
    this.w = w;
    tx = new TransactionTemplate(tm);
  }

  @Scheduled(
      fixedDelayString = "${JOB_INTERVAL_MS:10000}",
      initialDelayString = "${JOB_INITIAL_DELAY_MS:10000}")
  public void run() {
    for (int i = 0; i < 20; i++) {
      var event = claim();
      if (event == null) break;
      String id = s(event, "id"), kind = s(event, "kind"), no = s(event, "aggregate_id");
      try {
        switch (kind) {
          case "BENEFITS" -> shop.benefits(no);
          case "REFUND" -> refund(no, n(event, "attempts"));
          case "TRANSFER" -> transfer(no, n(event, "attempts"));
          default -> throw new IllegalStateException("Unknown event");
        }
        d.exec("UPDATE outbox SET state='DONE' WHERE id=?", id);
      } catch (Exception ex) {
        if (ex instanceof Problem p
            && List.of("CONFIG_REQUIRED", "KEY_CONFIGURATION", "INSECURE_ENDPOINT", "SIGN_FAILED")
                .contains(p.code)) d.exec("UPDATE outbox SET attempts=0 WHERE id=?", id);
        d.exec(
            "UPDATE outbox SET state='READY',next_at=?,last_error=? WHERE id=?",
            d.now() + Math.min(3600000, 30000L * (1L << Math.min(6, n(event, "attempts")))),
            ex instanceof Problem p ? p.code : "PROCESSING_ERROR",
            id);
        tx.executeWithoutResult(
            t ->
                d.issue(
                    "OUTBOX",
                    id,
                    "异步任务需要关注；错误码 " + (ex instanceof Problem p ? p.code : "PROCESSING_ERROR")));
      }
    }
  }

  private Map<String, Object> claim() {
    return tx.execute(
        t -> {
          var rows =
              d.all(
                  "SELECT * FROM outbox WHERE (state='READY' OR state='SENDING') AND next_at<=?"
                      + " ORDER BY created_at LIMIT 1 FOR UPDATE",
                  d.now());
          if (rows.isEmpty()) return null;
          var row = rows.getFirst();
          d.exec(
              "UPDATE outbox SET state='SENDING',attempts=attempts+1,next_at=? WHERE id=?",
              d.now() + 120000,
              s(row, "id"));
          return row;
        });
  }

  public void refund(String no, long attempt) {
    var r = d.one("SELECT * FROM refund WHERE id=?", no);
    if (s(r, "state").equals("SUCCESS") || s(r, "state").equals("CLOSED")) return;
    var o = d.one("SELECT * FROM shop_order WHERE id=?", s(r, "order_id"));
    if (c.dev() || n(r, "amount") == 0) {
      finance.refundSuccess(no, "LOCAL-" + no);
      return;
    }
    d.exec("UPDATE refund SET state='PROCESSING' WHERE id=? AND state='REVIEWED'", no);
    Map<String, Object> result = attempt == 0 ? w.refund(r, o) : w.queryRefund(no);
    refundResult(no, result);
    if (!List.of("SUCCESS", "CLOSED")
        .contains(s(d.one("SELECT state FROM refund WHERE id=?", no), "state")))
      throw new Problem(503, "REFUND_PENDING", "退款待确认");
  }

  @SuppressWarnings("unchecked")
  public void refundResult(String no, Map<String, Object> result) {
    var r = d.one("SELECT * FROM refund WHERE id=?", no);
    var a = (Map<String, Object>) result.get("amount");
    Problem.require(
        s(result, "out_refund_no").equals(no)
            && s(result, "out_trade_no").equals(s(r, "order_id"))
            && a != null
            && n(a, "refund") == n(r, "amount")
            && s(a, "currency").equals("CNY"),
        "REFUND_MISMATCH",
        "退款渠道证据不匹配");
    if (s(result, "status").equals("SUCCESS")) finance.refundSuccess(no, s(result, "refund_id"));
    else if (List.of("ABNORMAL", "CLOSED").contains(s(result, "status"))) {
      d.exec("UPDATE refund SET state='ABNORMAL' WHERE id=? AND state<>'SUCCESS'", no);
      tx.executeWithoutResult(t -> d.issue("REFUND_ABNORMAL", no, "渠道退款异常，请人工核对，不得以新单号重复退款"));
    }
  }

  public void transfer(String no, long attempt) {
    var r = d.one("SELECT * FROM withdrawal WHERE id=?", no);
    if (List.of("SUCCESS", "FAILED", "REJECTED").contains(s(r, "state"))) return;
    if (c.dev()) throw new Problem(503, "DEV_CONFIRM_REQUIRED", "开发环境需显式确认测试出款");
    Problem.require(c.enabled("TRANSFER_ENABLED"), "TRANSFER_DISABLED", "真实出款未开放");
    var result =
        attempt == 0
            ? w.transfer(
                r, s(d.one("SELECT openid FROM member WHERE id=?", n(r, "member_id")), "openid"))
            : w.queryTransfer(no);
    validateTransfer(no, result);
    finance.transferResult(no, result);
    if (!List.of("SUCCESS", "FAILED")
        .contains(s(d.one("SELECT state FROM withdrawal WHERE id=?", no), "state")))
      throw new Problem(503, "TRANSFER_UNKNOWN", "转账尚无最终结果");
  }

  public void validateTransfer(String no, Map<String, Object> result) {
    var r = d.one("SELECT * FROM withdrawal WHERE id=?", no);
    Problem.require(
        s(result, "out_bill_no").equals(no) && n(result, "transfer_amount") == n(r, "net"),
        "TRANSFER_MISMATCH",
        "渠道出款单号或金额不匹配");
    if (result.containsKey("appid"))
      Problem.require(
          s(result, "appid").equals(c.required("WX_APP_ID")), "CHANNEL_MISMATCH", "出款AppID不匹配");
  }

  @Scheduled(
      fixedDelayString = "${MAINTENANCE_INTERVAL_MS:60000}",
      initialDelayString = "${JOB_INITIAL_DELAY_MS:10000}")
  public void maintain() {
    for (var o :
        d.all(
            "SELECT * FROM shop_order WHERE state='CREATED' AND expires_at<? ORDER BY created_at"
                + " LIMIT 50",
            d.now()))
      try {
        shop.cancel(n(o, "member_id"), s(o, "id"));
      } catch (Exception e) {
        tx.executeWithoutResult(t -> d.issue("PAYMENT_UNKNOWN", s(o, "id"), "超时订单需原单查单确认，预占仍保留"));
      }
    try {
      finance.settleCommissions();
    } catch (Exception e) {
      tx.executeWithoutResult(t -> d.issue("COMMISSION_SETTLE", "JOB", "佣金结算未完成，请检查数据库和账本"));
    }
    d.exec("DELETE FROM session_token WHERE expires_at<?", d.now());
    d.exec("DELETE FROM quote WHERE expires_at<?", d.now() - 86400000);
    d.exec("DELETE FROM rate_limit WHERE window_at<?", d.now() - 86400000);
  }

  public Map<String, Object> reconcile(long actor) {
    long diffs = 0;
    var members = d.all("SELECT * FROM member ORDER BY id");
    for (var m : members)
      for (String asset : List.of("points", "points_held", "available", "reserved", "debt")) {
        long actual =
            d.count(
                "SELECT COALESCE(SUM(amount),0) FROM ledger WHERE member_id=? AND asset=?",
                n(m, "id"),
                asset);
        if (actual != n(m, asset)) {
          diffs++;
          String resource = n(m, "id") + ":" + asset;
          tx.executeWithoutResult(
              t -> d.issue("LEDGER_DIFF", resource, "余额与流水不符，差额 " + (n(m, asset) - actual)));
        }
      }
    for (var o :
        d.all(
            "SELECT * FROM shop_order WHERE payment_state='SUCCESS' AND cash_due>0 ORDER BY"
                + " paid_at")) {
      if (c.dev()) continue;
      try {
        var r = w.queryPayment(s(o, "id"));
        if (!s(r, "trade_state").equals("SUCCESS")) {
          diffs++;
          tx.executeWithoutResult(t -> d.issue("CHANNEL_DIFF", s(o, "id"), "本地已支付与微信查询状态不符"));
        } else shop.paymentEvidence(r);
      } catch (Exception e) {
        diffs++;
        tx.executeWithoutResult(
            t -> d.issue("CHANNEL_UNVERIFIED", s(o, "id"), "本次渠道对账无法确认，不能视为账实一致"));
      }
    }
    if (!c.dev()) {
      for (var r :
          d.all("SELECT * FROM refund WHERE state='SUCCESS' AND amount>0 ORDER BY created_at"))
        try {
          var proof = w.queryRefund(s(r, "id"));
          refundResult(s(r, "id"), proof);
          if (!s(proof, "status").equals("SUCCESS"))
            throw new Problem(409, "REFUND_CHANNEL_DIFF", "退款渠道不一致");
        } catch (Exception e) {
          diffs++;
          tx.executeWithoutResult(
              t -> d.issue("REFUND_CHANNEL_DIFF", s(r, "id"), "已退款记录与本次渠道核对结果不一致或无法确认"));
        }
      for (var r : d.all("SELECT * FROM withdrawal WHERE state='SUCCESS' ORDER BY created_at"))
        try {
          var proof = w.queryTransfer(s(r, "id"));
          validateTransfer(s(r, "id"), proof);
          if (!s(proof, "state").equals("SUCCESS"))
            throw new Problem(409, "TRANSFER_CHANNEL_DIFF", "出款渠道不一致");
        } catch (Exception e) {
          diffs++;
          tx.executeWithoutResult(
              t -> d.issue("TRANSFER_CHANNEL_DIFF", s(r, "id"), "本地已出款与本次渠道核对结果不一致或无法确认"));
        }
    }
    long id = d.id(), finalDiffs = diffs;
    tx.executeWithoutResult(
        t -> {
          d.exec(
              "INSERT INTO reconcile_batch VALUES(?,?,?,?,?,?,?)",
              id,
              LocalDateText(),
              d.count(
                  "SELECT COALESCE(SUM(cash_due),0) FROM shop_order WHERE payment_state='SUCCESS'"),
              d.count("SELECT COALESCE(SUM(amount),0) FROM refund WHERE state='SUCCESS'"),
              d.count("SELECT COALESCE(SUM(net),0) FROM withdrawal WHERE state='SUCCESS'"),
              finalDiffs,
              d.now());
          d.audit(
              actor,
              "RECONCILE",
              "" + id,
              Map.of("diffs", finalDiffs, "channelMode", c.dev() ? "DEVELOPMENT" : "WECHAT_QUERY"));
        });
    return d.one("SELECT * FROM reconcile_batch WHERE id=?", id);
  }

  private String LocalDateText() {
    return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
  }
}
