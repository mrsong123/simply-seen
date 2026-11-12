package com.sujian.mall;

import static com.sujian.mall.Db.*;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Commerce {
  final Db d;
  final Settings c;
  final Wechat w;
  final org.springframework.transaction.support.TransactionTemplate tx;

  public Commerce(
      Db d, Settings c, Wechat w, org.springframework.transaction.PlatformTransactionManager tm) {
    this.d = d;
    this.c = c;
    this.w = w;
    this.tx = new org.springframework.transaction.support.TransactionTemplate(tm);
  }

  public Map<String, Object> rules() {
    var rows =
        d.all(
            "SELECT * FROM rule_version WHERE state='ACTIVE' ORDER BY created_at DESC,id DESC LIMIT"
                + " 1");
    Problem.require(!rows.isEmpty(), "RULE_REQUIRED", "请先发布交易规则");
    var rule = d.decode(s(rows.getFirst(), "config"));
    rule.put("version", n(rows.getFirst(), "id"));
    return rule;
  }

  public List<Map<String, Object>> products(String category, String search) {
    var args = new ArrayList<Object>();
    String sql =
        "SELECT p.*, (SELECT MIN(price) FROM sku WHERE product_id=p.id) AS price, (SELECT"
            + " SUM(stock-held) FROM sku WHERE product_id=p.id) AS stock FROM product p WHERE"
            + " status='ON_SALE'";
    if (!category.isBlank()) {
      sql += " AND category_id=?";
      args.add(Long.parseLong(category));
    }
    if (!search.isBlank()) {
      sql += " AND name LIKE ?";
      args.add("%" + search.substring(0, Math.min(80, search.length())) + "%");
    }
    return d.all(sql + " ORDER BY featured DESC,id LIMIT 100", args.toArray());
  }

  public Map<String, Object> product(long id) {
    var p = d.one("SELECT * FROM product WHERE id=? AND status='ON_SALE'", id);
    p.put(
        "skus",
        d.all(
            "SELECT id,name,price,stock-held AS stock FROM sku WHERE product_id=? ORDER BY id",
            id));
    return p;
  }

  public List<Map<String, Object>> addresses(long uid) {
    return d.all("SELECT * FROM address WHERE member_id=? ORDER BY is_default DESC,id DESC", uid);
  }

  @Transactional
  public Object address(long uid, Map<String, Object> b) {
    String phone = s(b, "phone");
    Problem.require(
        s(b, "name").length() > 0
            && s(b, "name").length() <= 60
            && phone.matches("1[3-9][0-9]{9}")
            && !s(b, "region").isBlank()
            && !s(b, "detail").isBlank()
            && s(b, "detail").length() <= 240,
        "INVALID_INPUT",
        "请填写姓名、有效手机号、地区和详细地址");
    long id = n(b, "id");
    d.one("SELECT id FROM member WHERE id=? FOR UPDATE", uid);
    if (b(b, "is_default")) d.exec("UPDATE address SET is_default=FALSE WHERE member_id=?", uid);
    if (id == 0) {
      id = d.id();
      d.exec(
          "INSERT INTO address VALUES(?,?,?,?,?,?,?)",
          id,
          uid,
          s(b, "name"),
          phone,
          s(b, "region"),
          s(b, "detail"),
          b(b, "is_default"));
    } else {
      d.one("SELECT id FROM address WHERE id=? AND member_id=?", id, uid);
      d.exec(
          "UPDATE address SET name=?,phone=?,region=?,detail=?,is_default=? WHERE id=? AND"
              + " member_id=?",
          s(b, "name"),
          phone,
          s(b, "region"),
          s(b, "detail"),
          b(b, "is_default"),
          id,
          uid);
    }
    return Map.of("id", id);
  }

  public List<Map<String, Object>> cart(long uid) {
    return d.all(
        "SELECT c.sku_id,c.quantity,s.name AS sku_name,s.price,s.stock-s.held AS stock,p.id AS"
            + " product_id,p.name,p.image,p.color,p.status FROM cart_item c JOIN sku s ON"
            + " c.sku_id=s.id JOIN product p ON s.product_id=p.id WHERE c.member_id=? ORDER BY"
            + " p.id",
        uid);
  }

  @Transactional
  public void cart(long uid, long sku, int qty) {
    Problem.require(qty >= 0 && qty <= 99, "INVALID_QUANTITY", "每种规格最多99件");
    d.one("SELECT id FROM member WHERE id=? FOR UPDATE", uid);
    d.exec("DELETE FROM cart_item WHERE member_id=? AND sku_id=?", uid, sku);
    if (qty > 0) {
      d.one(
          "SELECT s.id FROM sku s JOIN product p ON p.id=s.product_id WHERE s.id=? AND"
              + " p.status='ON_SALE'",
          sku);
      d.exec("INSERT INTO cart_item VALUES(?,?,?)", uid, sku, qty);
    }
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public Map<String, Object> quote(long uid, Map<String, Object> b) {
    var m = d.one("SELECT * FROM member WHERE id=?", uid);
    long address = n(b, "addressId");
    var addr = d.one("SELECT * FROM address WHERE id=? AND member_id=?", address, uid);
    List<Map<String, Object>> items = (List<Map<String, Object>>) b.get("items");
    Problem.require(
        items != null && !items.isEmpty() && items.size() <= 50, "INVALID_ITEMS", "请选择1至50种商品");
    var rule = rules();
    TreeMap<Long, Integer> quantities = new TreeMap<>();
    for (var i : items) {
      int qty = Math.toIntExact(n(i, "quantity"));
      Problem.require(qty > 0 && qty <= 99, "INVALID_QUANTITY", "购买数量应为1至99");
      quantities.merge(n(i, "skuId"), qty, Integer::sum);
    }
    var lines = new ArrayList<Map<String, Object>>();
    long total = 0, maxPoints = 0;
    for (var entry : quantities.entrySet()) {
      long id = entry.getKey();
      int qty = entry.getValue();
      Problem.require(qty <= 99, "INVALID_QUANTITY", "每种规格最多99件");
      var s =
          d.one(
              "SELECT s.*,p.name AS"
                  + " product_name,p.image,p.status,p.points_percent,p.commission_percent FROM sku"
                  + " s JOIN product p ON s.product_id=p.id WHERE s.id=?",
              id);
      Problem.require(
          s(s, "status").equals("ON_SALE") && n(s, "stock") - n(s, "held") >= qty,
          "STOCK_NOT_ENOUGH",
          "商品已下架或库存不足");
      long amount = Math.multiplyExact(n(s, "price"), qty);
      long allowed = amount * n(s, "points_percent") / 100 / 100;
      var line = new LinkedHashMap<String, Object>();
      line.put("skuId", id);
      line.put("productId", n(s, "product_id"));
      line.put("name", s(s, "product_name") + " · " + s(s, "name"));
      line.put("image", s(s, "image"));
      line.put("quantity", qty);
      line.put("unitPrice", n(s, "price"));
      line.put("cost", n(s, "cost"));
      line.put("commissionPercent", n(s, "commission_percent"));
      line.put("amount", amount);
      line.put("maxPoints", allowed);
      lines.add(line);
      total += amount;
      maxPoints += allowed;
    }
    long requested = n(b, "pointsRequested");
    Problem.require(
        requested >= 0 && requested <= maxPoints && requested <= n(m, "points"),
        "POINTS_NOT_ENOUGH",
        "积分不足或超过商品抵扣上限");
    long left = requested;
    for (var line : lines) {
      long points = Math.min(left, n(line, "maxPoints"));
      line.put("points", points);
      line.put("cash", n(line, "amount") - points * 100);
      left -= points;
    }
    long freight = total >= n(rule, "freeShippingFen") ? 0 : n(rule, "freightFen");
    long cash = total + freight - requested * 100;
    Problem.require(cash >= 0 && cash <= 100000000, "INVALID_AMOUNT", "订单金额超出支持范围");
    rule.put("beneficiaries", beneficiaries(m));
    String id = d.no("Q");
    var q = new LinkedHashMap<String, Object>();
    q.put("quoteId", id);
    q.put("items", lines);
    q.put("itemAmountFen", total);
    q.put("freightFen", freight);
    q.put("pointsUsed", requested);
    q.put("pointsAllowed", Math.min(maxPoints, n(m, "points")));
    q.put("cashDueFen", cash);
    q.put("address", addr);
    q.put("rule", rule);
    q.put("expiresAt", d.now() + 300000);
    d.exec("INSERT INTO quote VALUES(?,?,?,?,?)", id, uid, address, d.encode(q), n(q, "expiresAt"));
    return q;
  }

  private List<Map<String, Object>> beneficiaries(Map<String, Object> m) {
    var result = new ArrayList<Map<String, Object>>();
    long parent = n(m, "inviter_id");
    for (int level = 1; level <= 2 && parent != 0; level++) {
      var p = d.one("SELECT * FROM member WHERE id=?", parent);
      if (b(p, "promoter") && !b(p, "blocked")) result.add(Map.of("id", parent, "level", level));
      parent = n(p, "inviter_id");
    }
    return result;
  }

  public Object idem(
      long uid,
      String operation,
      String key,
      Object body,
      java.util.function.Supplier<Object> action) {
    Problem.require(
        key != null && key.matches("[A-Za-z0-9_-]{8,80}"), "IDEMPOTENCY_REQUIRED", "请提供8至80位幂等键");
    d.one("SELECT id FROM member WHERE id=? FOR UPDATE", uid);
    String scope = uid + ":" + operation + ":" + key, hash = hash(d.encode(body));
    var rows = d.all("SELECT * FROM idempotency WHERE scope_key=?", scope);
    if (!rows.isEmpty()) {
      Problem.require(
          s(rows.getFirst(), "payload_hash").equals(hash),
          "IDEMPOTENCY_CONFLICT",
          "相同请求编号不能提交不同内容");
      return d.decode(s(rows.getFirst(), "response"));
    }
    Object response = action.get();
    d.exec("INSERT INTO idempotency VALUES(?,?,?,?)", scope, hash, d.encode(response), d.now());
    return response;
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public Object create(long uid, String key, Map<String, Object> b) {
    return idem(
        uid,
        "order",
        key,
        b,
        () -> {
          var qr = d.one("SELECT * FROM quote WHERE id=? AND member_id=?", s(b, "quoteId"), uid);
          Problem.require(n(qr, "expires_at") > d.now(), "QUOTE_EXPIRED", "报价已过期，请重新结算");
          var q = d.decode(s(qr, "payload"));
          Problem.require(n(qr, "address_id") == n(b, "addressId"), "QUOTE_EXPIRED", "地址已变更，请重新结算");
          var lines = (List<Map<String, Object>>) q.get("items");
          long points = n(q, "pointsUsed");
          var m = d.one("SELECT * FROM member WHERE id=? FOR UPDATE", uid);
          Problem.require(n(m, "points") >= points, "POINTS_NOT_ENOUGH", "积分余额不足");
          for (var l : lines) {
            var sku =
                d.one(
                    "SELECT s.*,p.status,p.points_percent,p.commission_percent FROM sku s JOIN"
                        + " product p ON p.id=s.product_id WHERE s.id=? FOR UPDATE",
                    n(l, "skuId"));
            Problem.require(
                s(sku, "status").equals("ON_SALE")
                    && n(sku, "price") == n(l, "unitPrice")
                    && n(sku, "cost") == n(l, "cost")
                    && n(sku, "commission_percent") == n(l, "commissionPercent")
                    && n(l, "points") * 100 <= n(l, "amount") * n(sku, "points_percent") / 100,
                "QUOTE_EXPIRED",
                "商品或价格已变更，请重新报价");
            Problem.require(
                d.exec(
                        "UPDATE sku SET held=held+?,version=version+1 WHERE id=? AND stock-held>=?",
                        n(l, "quantity"),
                        n(l, "skuId"),
                        n(l, "quantity"))
                    == 1,
                "STOCK_NOT_ENOUGH",
                "库存不足");
          }
          String no = d.no("SJ");
          long now = d.now();
          d.exec(
              "INSERT INTO"
                  + " shop_order(id,member_id,state,payment_state,item_amount,freight,points_used,cash_due,address_snapshot,rule_snapshot,remark,expires_at,created_at)"
                  + " VALUES(?,?,'CREATED','INIT',?,?,?,?,?,?,?,?,?)",
              no,
              uid,
              n(q, "itemAmountFen"),
              n(q, "freightFen"),
              points,
              n(q, "cashDueFen"),
              d.encode(q.get("address")),
              d.encode(q.get("rule")),
              s(b, "buyerRemark").substring(0, Math.min(240, s(b, "buyerRemark").length())),
              now + c.integer("ORDER_EXPIRE_MINUTES", 15) * 60000L,
              now);
          for (var l : lines)
            d.exec(
                "INSERT INTO"
                    + " order_item(id,order_id,sku_id,product_id,name,image,quantity,unit_price,cost,cash_amount,points_amount,commission_percent)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                d.id(),
                no,
                n(l, "skuId"),
                n(l, "productId"),
                s(l, "name"),
                s(l, "image"),
                n(l, "quantity"),
                n(l, "unitPrice"),
                n(l, "cost"),
                n(l, "cash"),
                n(l, "points"),
                n(l, "commissionPercent"));
          if (points > 0) {
            asset(uid, "points", -points, no + ":points-hold", no, "订单积分预占");
            asset(uid, "points_held", points, no + ":points-held", no, "订单积分冻结");
          }
          for (var l : lines)
            d.exec("DELETE FROM cart_item WHERE member_id=? AND sku_id=?", uid, n(l, "skuId"));
          d.exec("DELETE FROM quote WHERE id=?", s(b, "quoteId"));
          if (n(q, "cashDueFen") == 0) paid(no, "POINTS-" + no, 0);
          return order(uid, no);
        });
  }

  public List<Map<String, Object>> orders(long uid) {
    var rows =
        d.all("SELECT * FROM shop_order WHERE member_id=? ORDER BY created_at DESC LIMIT 100", uid);
    for (var o : rows)
      o.put("items", d.all("SELECT * FROM order_item WHERE order_id=? ORDER BY id", s(o, "id")));
    return rows;
  }

  public Map<String, Object> order(long uid, String no) {
    var o = d.one("SELECT * FROM shop_order WHERE id=? AND member_id=?", no, uid);
    o.put("items", d.all("SELECT * FROM order_item WHERE order_id=? ORDER BY id", no));
    o.put("refunds", d.all("SELECT * FROM refund WHERE order_id=? ORDER BY created_at DESC", no));
    o.put("address", d.decode(s(o, "address_snapshot")));
    return o;
  }

  public void asset(long uid, String asset, long delta, String event, String order, String note) {
    Problem.require(
        List.of("points", "points_held", "available", "reserved", "debt").contains(asset),
        "INVALID_ASSET",
        "资产类型无效");
    var m = d.one("SELECT * FROM member WHERE id=? FOR UPDATE", uid);
    if (d.count("SELECT COUNT(*) FROM ledger WHERE event_key=?", event) > 0) return;
    long balance = Math.addExact(n(m, asset), delta);
    Problem.require(balance >= 0, "BALANCE_NOT_ENOUGH", "可用资产不足");
    d.exec("UPDATE member SET " + asset + "=? WHERE id=?", balance, uid);
    d.exec(
        "INSERT INTO ledger VALUES(?,?,?,?,?,?,?,?,?)",
        d.id(),
        uid,
        event,
        asset,
        delta,
        balance,
        order,
        note,
        d.now());
  }

  @Transactional
  public void paid(String no, String transaction, long amount) {
    var o = d.one("SELECT * FROM shop_order WHERE id=? FOR UPDATE", no);
    Problem.require(n(o, "cash_due") == amount, "PAYMENT_AMOUNT_MISMATCH", "渠道实付与订单金额不符");
    if (s(o, "payment_state").equals("SUCCESS")) {
      Problem.require(s(o, "transaction_id").equals(transaction), "PAYMENT_CONFLICT", "渠道交易单号冲突");
      return;
    }
    Problem.require(s(o, "state").equals("CREATED"), "PAYMENT_CONFLICT", "订单状态冲突，需人工核对");
    long uid = n(o, "member_id");
    d.one("SELECT id FROM member WHERE id=? FOR UPDATE", uid);
    for (var i : d.all("SELECT * FROM order_item WHERE order_id=? ORDER BY sku_id", no)) {
      Problem.require(
          d.exec(
                  "UPDATE sku SET stock=stock-?,held=held-?,version=version+1 WHERE id=? AND"
                      + " held>=? AND stock>=?",
                  n(i, "quantity"),
                  n(i, "quantity"),
                  n(i, "sku_id"),
                  n(i, "quantity"),
                  n(i, "quantity"))
              == 1,
          "STOCK_INCONSISTENT",
          "库存预占异常");
    }
    if (n(o, "points_used") > 0)
      asset(uid, "points_held", -n(o, "points_used"), no + ":points-commit", no, "支付确认核销积分");
    d.exec(
        "UPDATE shop_order SET state='PAID',payment_state='SUCCESS',transaction_id=?,paid_at=?"
            + " WHERE id=?",
        transaction,
        d.now(),
        no);
    d.event(no + ":benefits", "BENEFITS", no);
  }

  @Transactional
  public void benefits(String no) {
    var o = d.one("SELECT * FROM shop_order WHERE id=? FOR UPDATE", no);
    if (!s(o, "payment_state").equals("SUCCESS")) return;
    long uid = n(o, "member_id");
    var rule = d.decode(s(o, "rule_snapshot"));
    long points =
        Math.max(0, n(o, "cash_due") - n(o, "freight") - n(o, "cash_refunded"))
            / 10000
            * n(rule, "pointsPer100Yuan");
    if (points > 0) asset(uid, "points", points, no + ":earned", no, "有效现金消费赠积分");
    commission(o, rule);
  }

  @SuppressWarnings("unchecked")
  private void commission(Map<String, Object> o, Map<String, Object> rule) {
    if (!b(rule, "distributionEnabled")) return;
    String no = s(o, "id");
    if (d.count("SELECT COUNT(*) FROM commission WHERE order_id=?", no) > 0) return;
    long pool = 0;
    for (var l : d.all("SELECT * FROM order_item WHERE order_id=?", no)) {
      long cash = n(l, "cash_amount");
      long margin = Math.max(0, cash - n(l, "cost") * n(l, "quantity"));
      pool +=
          Math.min(
              cash * n(l, "commission_percent") / 100,
              margin * n(rule, "marginSharePercent") / 100);
    }
    d.one("SELECT scope_key FROM system_guard WHERE scope_key='COMMISSION' FOR UPDATE");
    long monthStart =
        java.time.Instant.ofEpochMilli(n(o, "paid_at"))
            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli();
    long used =
        d.count(
            "SELECT COALESCE(SUM(original_amount),0) FROM commission WHERE created_at>=?",
            monthStart);
    pool = Math.min(pool, Math.max(0, n(rule, "commissionBudgetFen") - used));
    long base = Math.max(0, n(o, "cash_due") - n(o, "freight"));
    long first = base * n(rule, "level1Bps") / 10000,
        second = base * n(rule, "level2Bps") / 10000,
        sum = first + second;
    if (sum > pool && sum > 0) {
      first = pool * first / sum;
      second = pool - first;
    }
    for (var person : (List<Map<String, Object>>) rule.get("beneficiaries")) {
      long level = n(person, "level"), amount = level == 1 ? first : second;
      if (amount > 0)
        d.exec(
            "INSERT INTO"
                + " commission(id,order_id,beneficiary_id,buyer_id,level_no,amount,original_amount,state,rule_snapshot,created_at)"
                + " VALUES(?,?,?,?,?,?,?,'PENDING',?,?)",
            d.id(),
            no,
            n(person, "id"),
            n(o, "member_id"),
            level,
            amount,
            amount,
            d.encode(rule),
            d.now());
    }
  }

  @Transactional
  public void closeLocal(String no) {
    var o = d.one("SELECT * FROM shop_order WHERE id=? FOR UPDATE", no);
    if (!s(o, "state").equals("CREATED")) return;
    long uid = n(o, "member_id");
    d.one("SELECT id FROM member WHERE id=? FOR UPDATE", uid);
    for (var i : d.all("SELECT * FROM order_item WHERE order_id=? ORDER BY sku_id", no))
      d.exec(
          "UPDATE sku SET held=held-?,version=version+1 WHERE id=? AND held>=?",
          n(i, "quantity"),
          n(i, "sku_id"),
          n(i, "quantity"));
    long p = n(o, "points_used");
    if (p > 0) {
      asset(uid, "points_held", -p, no + ":points-unhold", no, "订单关闭解除冻结");
      asset(uid, "points", p, no + ":points-return", no, "订单关闭退回积分");
    }
    d.exec("UPDATE shop_order SET state='CLOSED',payment_state='CLOSED' WHERE id=?", no);
  }

  public Object prepay(long uid, String no) {
    var o = order(uid, no);
    Problem.require(
        s(o, "state").equals("CREATED") && n(o, "expires_at") > d.now(),
        "ORDER_STATE",
        "订单已支付或已过期");
    if (c.dev()) return Map.of("mode", "DEVELOPMENT", "orderNo", no);
    tx.executeWithoutResult(
        t -> {
          var locked =
              d.one("SELECT * FROM shop_order WHERE id=? AND member_id=? FOR UPDATE", no, uid);
          Problem.require(
              s(locked, "state").equals("CREATED") && n(locked, "expires_at") > d.now(),
              "ORDER_STATE",
              "订单不可支付");
          d.exec("UPDATE shop_order SET payment_state='PREPAY' WHERE id=? AND state='CREATED'", no);
        });
    var result = w.prepay(o, s(d.one("SELECT openid FROM member WHERE id=?", uid), "openid"));
    d.exec(
        "UPDATE shop_order SET payment_state='PREPAY' WHERE id=? AND state='CREATED' AND"
            + " payment_state IN ('INIT','PREPAY')",
        no);
    return result;
  }

  public void synchronizePayment(String no) {
    var o = d.one("SELECT * FROM shop_order WHERE id=?", no);
    if (s(o, "payment_state").equals("SUCCESS")) return;
    var result = w.queryPayment(no);
    tx.executeWithoutResult(t -> paymentEvidence(result));
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void paymentEvidence(Map<String, Object> r) {
    Problem.require(
        s(r, "mchid").equals(c.required("WX_MCH_ID"))
            && s(r, "appid").equals(c.required("WX_APP_ID")),
        "CHANNEL_MISMATCH",
        "商户或小程序编号不符");
    if (s(r, "trade_state").equals("SUCCESS")) {
      Problem.require(!s(r, "transaction_id").isBlank(), "PAYMENT_EVIDENCE", "缺少可信渠道交易单号");
      var target = d.one("SELECT member_id FROM shop_order WHERE id=?", s(r, "out_trade_no"));
      var payer = (Map<String, Object>) r.get("payer");
      Problem.require(
          payer != null
              && s(payer, "openid")
                  .equals(
                      s(
                          d.one("SELECT openid FROM member WHERE id=?", n(target, "member_id")),
                          "openid")),
          "PAYMENT_PAYER_MISMATCH",
          "渠道付款人与订单归属不符");
      var amount = (Map<String, Object>) r.get("amount");
      Problem.require(s(amount, "currency").equals("CNY"), "CURRENCY_MISMATCH", "支付币种不符");
      paid(s(r, "out_trade_no"), s(r, "transaction_id"), n(amount, "total"));
    }
  }

  public void cancel(long uid, String no) {
    var o = order(uid, no);
    if (s(o, "state").equals("CLOSED")) return;
    Problem.require(s(o, "state").equals("CREATED"), "ORDER_STATE", "已支付订单请申请售后");
    boolean closed =
        Boolean.TRUE.equals(
            tx.execute(
                t -> {
                  var locked =
                      d.one(
                          "SELECT * FROM shop_order WHERE id=? AND member_id=? FOR UPDATE",
                          no,
                          uid);
                  if (!s(locked, "state").equals("CREATED"))
                    return s(locked, "state").equals("CLOSED");
                  if (c.dev() || s(locked, "payment_state").equals("INIT")) {
                    closeLocal(no);
                    return true;
                  }
                  return false;
                }));
    if (closed) return;
    synchronizePayment(no);
    o = order(uid, no);
    Problem.require(s(o, "state").equals("CREATED"), "ORDER_PAID", "订单已支付");
    w.close(no);
    tx.executeWithoutResult(t -> closeLocal(no));
  }

  @Transactional
  public void ship(long admin, String no, Map<String, Object> body) {
    var o = d.one("SELECT * FROM shop_order WHERE id=? FOR UPDATE", no);
    Problem.require(
        s(o, "state").equals("PAID")
            && d.count(
                    "SELECT COUNT(*) FROM refund WHERE order_id=? AND state IN"
                        + " ('REQUESTED','REVIEWED','PROCESSING','ABNORMAL')",
                    no)
                == 0,
        "ORDER_STATE",
        "订单不可发货或有进行中的售后");
    Problem.require(
        !s(body, "carrier").isBlank() && s(body, "trackingNo").matches("[A-Za-z0-9-]{5,80}"),
        "INVALID_INPUT",
        "请填写物流公司与有效运单号");
    d.exec(
        "UPDATE shop_order SET state='SHIPPED',carrier=?,tracking_no=? WHERE id=?",
        s(body, "carrier"),
        s(body, "trackingNo"),
        no);
    d.audit(admin, "SHIP", no, body);
  }

  @Transactional
  public void receive(long uid, String no) {
    var o = d.one("SELECT * FROM shop_order WHERE id=? AND member_id=? FOR UPDATE", no, uid);
    Problem.require(s(o, "state").equals("SHIPPED"), "ORDER_STATE", "订单尚未发货或已收货");
    d.exec("UPDATE shop_order SET state='COMPLETED',completed_at=? WHERE id=?", d.now(), no);
  }
}
