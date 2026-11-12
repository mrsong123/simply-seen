package com.sujian.mall;

import static com.sujian.mall.Db.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class Api {
  final Db d;
  final Settings c;
  final Auth auth;
  final Commerce shop;
  final Finance f;
  final Jobs jobs;
  final Wechat w;
  final Logistics logistics;

  public Api(
      Db d, Settings c, Auth a, Commerce s, Finance f, Jobs j, Wechat w, Logistics logistics) {
    this.d = d;
    this.c = c;
    auth = a;
    shop = s;
    this.f = f;
    jobs = j;
    this.w = w;
    this.logistics = logistics;
  }

  @GetMapping("/config")
  public Object config() {
    return c.publicConfig();
  }

  @GetMapping("/categories")
  public Object categories() {
    return d.all("SELECT * FROM category ORDER BY sort_no,id");
  }

  @GetMapping("/products")
  public Object products(
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "") String search) {
    return shop.products(category, search);
  }

  @GetMapping("/products/{id}")
  public Object product(@PathVariable long id) {
    return shop.product(id);
  }

  @GetMapping("/rules/public")
  public Object rules() {
    var r = shop.rules();
    return Map.of(
        "version",
        r.get("version"),
        "pointsPer100Yuan",
        r.get("pointsPer100Yuan"),
        "freightFen",
        r.get("freightFen"),
        "freeShippingFen",
        r.get("freeShippingFen"),
        "settlementDays",
        r.get("settlementDays"),
        "level1Bps",
        r.get("level1Bps"),
        "level2Bps",
        r.get("level2Bps"),
        "distributionEnabled",
        r.get("distributionEnabled"));
  }

  @PostMapping("/auth/wechat")
  public Object login(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return auth.login(b, r.getRemoteAddr());
  }

  @PostMapping("/auth/dev")
  public Object dev(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    auth.limit("dev:" + r.getRemoteAddr(), 30);
    return auth.devLogin(b);
  }

  @PostMapping("/auth/admin")
  public Object adminLogin(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return auth.adminLogin(b, r.getRemoteAddr());
  }

  @PostMapping("/auth/logout")
  public void logout(HttpServletRequest r) {
    auth.logout(r);
  }

  @GetMapping("/me")
  public Object me(HttpServletRequest r) {
    return auth.profile(auth.member(r).id());
  }

  @PutMapping("/me")
  public void me(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    auth.updateProfile(auth.member(r).id(), b);
  }

  @PostMapping("/me/promoter")
  public void promoter(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    Problem.require(b(b, "accepted"), "AGREEMENT_REQUIRED", "请阅读并同意推广规则");
    auth.promote(auth.member(r).id());
  }

  @GetMapping("/me/ledger")
  public Object ledger(HttpServletRequest r) {
    return d.all(
        "SELECT * FROM ledger WHERE member_id=? ORDER BY created_at DESC,id DESC LIMIT 200",
        auth.member(r).id());
  }

  @GetMapping("/me/team")
  public Object team(HttpServletRequest r) {
    long uid = auth.member(r).id();
    var first =
        d.all(
            "SELECT id,name,created_at,promoter FROM member WHERE inviter_id=? ORDER BY created_at"
                + " DESC LIMIT 100",
            uid);
    var second =
        d.all(
            "SELECT m.id,m.name,m.created_at FROM member m JOIN member p ON m.inviter_id=p.id WHERE"
                + " p.inviter_id=? ORDER BY m.created_at DESC LIMIT 100",
            uid);
    return Map.of(
        "level1",
        first,
        "level2",
        second,
        "commissions",
        d.all(
            "SELECT id,order_id,level_no,amount,state,created_at,settled_at FROM commission WHERE"
                + " beneficiary_id=? ORDER BY created_at DESC LIMIT 200",
            uid));
  }

  @GetMapping("/me/withdrawals")
  public Object withdrawals(HttpServletRequest r) {
    return d.all(
        "SELECT * FROM withdrawal WHERE member_id=? ORDER BY created_at DESC LIMIT 100",
        auth.member(r).id());
  }

  @PostMapping("/me/withdrawals")
  public Object withdraw(
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return f.withdraw(auth.member(r).id(), key, b);
  }

  @GetMapping("/me/awards")
  public Object awards(HttpServletRequest r) {
    return d.all(
        "SELECT a.*,s.period,s.state FROM settlement_award a JOIN settlement s ON"
            + " s.id=a.settlement_id WHERE a.member_id=? AND s.state='APPROVED' ORDER BY s.period"
            + " DESC",
        auth.member(r).id());
  }

  @GetMapping("/addresses")
  public Object addresses(HttpServletRequest r) {
    return shop.addresses(auth.member(r).id());
  }

  @PostMapping("/addresses")
  public Object address(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return shop.address(auth.member(r).id(), b);
  }

  @DeleteMapping("/addresses/{id}")
  public void deleteAddress(@PathVariable long id, HttpServletRequest r) {
    d.exec("DELETE FROM address WHERE id=? AND member_id=?", id, auth.member(r).id());
  }

  @GetMapping("/cart")
  public Object cart(HttpServletRequest r) {
    return shop.cart(auth.member(r).id());
  }

  @PutMapping("/cart/{sku}")
  public void cart(
      @PathVariable long sku, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    shop.cart(auth.member(r).id(), sku, Math.toIntExact(n(b, "quantity")));
  }

  @PostMapping("/checkout/quote")
  public Object quote(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return shop.quote(auth.member(r).id(), b);
  }

  @PostMapping("/orders")
  public Object create(
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return shop.create(auth.member(r).id(), key, b);
  }

  @GetMapping("/orders")
  public Object orders(HttpServletRequest r) {
    return shop.orders(auth.member(r).id());
  }

  @GetMapping("/orders/{no}")
  public Object order(@PathVariable String no, HttpServletRequest r) {
    return shop.order(auth.member(r).id(), no);
  }

  @PostMapping("/orders/{no}/prepay")
  public Object prepay(@PathVariable String no, HttpServletRequest r) {
    return shop.prepay(auth.member(r).id(), no);
  }

  @PostMapping("/orders/{no}/cancel")
  public void cancel(@PathVariable String no, HttpServletRequest r) {
    shop.cancel(auth.member(r).id(), no);
  }

  @GetMapping("/orders/{no}/payment-status")
  public Object payStatus(@PathVariable String no, HttpServletRequest r) {
    long uid = auth.member(r).id();
    var o = shop.order(uid, no);
    if (!c.dev() && s(o, "state").equals("CREATED")) shop.synchronizePayment(no);
    return shop.order(uid, no);
  }

  @GetMapping("/orders/{no}/logistics")
  public Object logistics(@PathVariable String no, HttpServletRequest request) {
    return logistics.query(shop.order(auth.member(request).id(), no));
  }

  @PostMapping("/orders/{no}/receive")
  public void receive(@PathVariable String no, HttpServletRequest r) {
    shop.receive(auth.member(r).id(), no);
  }

  @PostMapping("/dev/orders/{no}/pay")
  public void devPay(@PathVariable String no, HttpServletRequest r) {
    Problem.require(c.dev(), "DEV_DISABLED", "仅开发环境允许测试付款");
    var o = shop.order(auth.member(r).id(), no);
    shop.paid(no, "DEV-" + no, n(o, "cash_due"));
    jobs.run();
  }

  @PostMapping("/refunds")
  public Object refund(
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return f.refund(auth.member(r).id(), key, b);
  }

  @GetMapping("/admin/me")
  public Object adminMe(HttpServletRequest r) {
    var a = auth.admin(r, "OPERATOR", "FINANCE");
    return d.one("SELECT id,username,role FROM admin_account WHERE id=?", a.id());
  }

  @PostMapping("/admin/password")
  public void password(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    auth.password(auth.admin(r, "OPERATOR", "FINANCE").id(), b);
  }

  @GetMapping("/admin/summary")
  public Object summary(HttpServletRequest r) {
    auth.admin(r, "OPERATOR", "FINANCE");
    return Map.of(
        "members",
        d.count("SELECT COUNT(*) FROM member"),
        "orders",
        d.count("SELECT COUNT(*) FROM shop_order"),
        "gmv",
        d.count(
            "SELECT COALESCE(SUM(cash_due-cash_refunded),0) FROM shop_order WHERE"
                + " payment_state='SUCCESS'"),
        "pendingOrders",
        d.count("SELECT COUNT(*) FROM shop_order WHERE state='PAID'"),
        "pendingRefunds",
        d.count("SELECT COUNT(*) FROM refund WHERE state='REQUESTED'"),
        "pendingWithdrawals",
        d.count("SELECT COUNT(*) FROM withdrawal WHERE state='REVIEWING'"),
        "issues",
        d.count("SELECT COUNT(*) FROM issue WHERE state='OPEN'"),
        "trend",
        d.all(
            "SELECT created_at,cash_due,state FROM shop_order ORDER BY created_at DESC LIMIT 200"));
  }

  @GetMapping("/admin/products")
  public Object adminProducts(HttpServletRequest r) {
    auth.admin(r, "OPERATOR");
    var rows = d.all("SELECT * FROM product ORDER BY id DESC LIMIT 200");
    for (var p : rows)
      p.put("skus", d.all("SELECT * FROM sku WHERE product_id=? ORDER BY id", n(p, "id")));
    return rows;
  }

  @PostMapping("/admin/products")
  @Transactional
  public Object saveProduct(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    long actor = auth.admin(r, "OPERATOR").id();
    String name = s(b, "name"), image = s(b, "image"), status = s(b, "status");
    Problem.require(
        name.length() > 0
            && name.length() <= 120
            && s(b, "description").length() <= 20000
            && (image.startsWith("https://") || image.matches("/art/[a-z]+\\.svg"))
            && List.of("ON_SALE", "OFF_SALE").contains(status)
            && n(b, "points_percent") >= 0
            && n(b, "points_percent") <= 100
            && n(b, "commission_percent") >= 0
            && n(b, "commission_percent") <= 10
            && s(b, "color").matches("#[0-9a-fA-F]{6}"),
        "INVALID_PRODUCT",
        "商品信息不完整，图片需HTTPS，佣金上限≤10%");
    d.one("SELECT id FROM category WHERE id=?", n(b, "category_id"));
    long id = n(b, "id");
    if (id == 0) {
      id = d.id();
      d.exec(
          "INSERT INTO product VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
          id,
          n(b, "category_id"),
          name,
          s(b, "subtitle"),
          s(b, "description"),
          image,
          s(b, "color"),
          status,
          b(b, "featured"),
          n(b, "points_percent"),
          n(b, "commission_percent"),
          d.now());
    } else {
      d.one("SELECT id FROM product WHERE id=? FOR UPDATE", id);
      d.exec(
          "UPDATE product SET"
              + " category_id=?,name=?,subtitle=?,description=?,image=?,color=?,status=?,featured=?,points_percent=?,commission_percent=?"
              + " WHERE id=?",
          n(b, "category_id"),
          name,
          s(b, "subtitle"),
          s(b, "description"),
          image,
          s(b, "color"),
          status,
          b(b, "featured"),
          n(b, "points_percent"),
          n(b, "commission_percent"),
          id);
    }
    @SuppressWarnings("unchecked")
    var skus = (List<Map<String, Object>>) b.get("skus");
    Problem.require(
        skus != null && !skus.isEmpty() && skus.size() <= 20, "INVALID_SKU", "需配置1至20个规格");
    for (var s : skus) {
      Problem.require(
          n(s, "price") > 0
              && n(s, "price") <= 10000000
              && n(s, "cost") >= 0
              && n(s, "cost") <= n(s, "price")
              && n(s, "stock") >= 0
              && n(s, "stock") <= 1000000
              && !s(s, "name").isBlank()
              && !s(s, "code").isBlank(),
          "INVALID_SKU",
          "规格价格、成本或库存无效");
      long sid = n(s, "id");
      if (sid == 0)
        d.exec(
            "INSERT INTO sku VALUES(?,?,?,?,?,?,?,0,0)",
            d.id(),
            id,
            s(s, "name"),
            s(s, "code"),
            n(s, "price"),
            n(s, "cost"),
            n(s, "stock"));
      else {
        var old = d.one("SELECT * FROM sku WHERE id=? AND product_id=? FOR UPDATE", sid, id);
        Problem.require(n(s, "stock") >= n(old, "held"), "STOCK_HELD", "库存不可低于已预占数量");
        d.exec(
            "UPDATE sku SET name=?,code=?,price=?,cost=?,stock=?,version=version+1 WHERE id=?",
            s(s, "name"),
            s(s, "code"),
            n(s, "price"),
            n(s, "cost"),
            n(s, "stock"),
            sid);
      }
    }
    d.audit(actor, "PRODUCT_SAVE", "" + id, Map.of("name", name, "status", status));
    return Map.of("id", id);
  }

  @PostMapping("/admin/categories")
  @Transactional
  public Object category(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    long actor = auth.admin(r, "OPERATOR").id();
    String name = s(b, "name").trim();
    Problem.require(!name.isBlank() && name.length() <= 60, "INVALID_INPUT", "分类名称无效");
    long id = n(b, "id");
    if (id == 0) {
      id = d.id();
      d.exec("INSERT INTO category VALUES(?,?,?)", id, name, n(b, "sort_no"));
    } else d.exec("UPDATE category SET name=?,sort_no=? WHERE id=?", name, n(b, "sort_no"), id);
    d.audit(actor, "CATEGORY_SAVE", "" + id, b);
    return Map.of("id", id);
  }

  @GetMapping("/admin/orders")
  public Object adminOrders(HttpServletRequest r) {
    auth.admin(r, "OPERATOR", "FINANCE");
    return d.all(
        "SELECT o.*,m.name AS member_name FROM shop_order o JOIN member m ON m.id=o.member_id ORDER"
            + " BY o.created_at DESC LIMIT 200");
  }

  @GetMapping("/admin/orders/{no}")
  public Object adminOrder(@PathVariable String no, HttpServletRequest r) {
    auth.admin(r, "OPERATOR", "FINANCE");
    var o = d.one("SELECT member_id FROM shop_order WHERE id=?", no);
    return shop.order(n(o, "member_id"), no);
  }

  @PostMapping("/admin/orders/{no}/ship")
  public void ship(
      @PathVariable String no, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    shop.ship(auth.admin(r, "OPERATOR").id(), no, b);
  }

  @GetMapping("/admin/refunds")
  public Object refunds(HttpServletRequest r) {
    auth.admin(r, "OPERATOR", "FINANCE");
    return d.all("SELECT * FROM refund ORDER BY created_at DESC LIMIT 200");
  }

  @PostMapping("/admin/refunds/{id}/review")
  public void reviewRefund(
      @PathVariable String id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    f.reviewRefund(auth.admin(r, "FINANCE").id(), id, b(b, "approve"), s(b, "reason"));
    jobs.run();
  }

  @GetMapping("/admin/members")
  public Object members(HttpServletRequest r) {
    auth.admin(r, "OPERATOR", "FINANCE");
    return d.all(
        "SELECT"
            + " id,name,invite_code,inviter_id,points,points_held,available,reserved,debt,promoter,blocked,created_at"
            + " FROM member ORDER BY created_at DESC LIMIT 200");
  }

  @PostMapping("/admin/members/{id}/block")
  @Transactional
  public void block(
      @PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    long actor = auth.admin(r).id();
    d.exec("UPDATE member SET blocked=? WHERE id=?", b(b, "blocked"), id);
    if (b(b, "blocked")) d.exec("DELETE FROM session_token WHERE actor_id=? AND kind='MEMBER'", id);
    d.audit(actor, "MEMBER_BLOCK", "" + id, b);
  }

  @GetMapping("/admin/commissions")
  public Object commissions(HttpServletRequest r) {
    auth.admin(r, "FINANCE");
    return d.all("SELECT * FROM commission ORDER BY created_at DESC LIMIT 200");
  }

  @GetMapping("/admin/ledger")
  public Object adminLedger(HttpServletRequest r) {
    auth.admin(r, "FINANCE");
    return d.all("SELECT * FROM ledger ORDER BY created_at DESC,id DESC LIMIT 500");
  }

  @GetMapping("/admin/withdrawals")
  public Object adminWithdrawals(HttpServletRequest r) {
    auth.admin(r, "FINANCE");
    return d.all("SELECT * FROM withdrawal ORDER BY created_at DESC LIMIT 200");
  }

  @PostMapping("/admin/withdrawals/{id}/review")
  public void reviewWithdraw(
      @PathVariable String id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    f.reviewWithdrawal(auth.admin(r, "FINANCE").id(), id, b);
  }

  @PostMapping("/admin/withdrawals/{id}/execute")
  public void execute(@PathVariable String id, HttpServletRequest r) {
    f.executeWithdrawal(auth.admin(r).id(), id);
    jobs.run();
  }

  @PostMapping("/admin/withdrawals/{id}/query")
  public void queryTransfer(@PathVariable String id, HttpServletRequest r) {
    auth.admin(r, "FINANCE");
    jobs.transfer(id, 1);
  }

  @PostMapping("/admin/dev/withdrawals/{id}/confirm")
  public void devTransfer(@PathVariable String id, HttpServletRequest r) {
    long actor = auth.admin(r).id();
    Problem.require(c.dev(), "DEV_DISABLED", "仅开发环境允许测试出款");
    f.transferResult(id, Map.of("state", "SUCCESS", "transfer_bill_no", "DEV-" + id));
    d.audit(actor, "DEV_TRANSFER", id, Map.of());
  }

  @GetMapping("/admin/rules")
  public Object adminRules(HttpServletRequest r) {
    auth.admin(r, "OPERATOR", "FINANCE");
    return d.all("SELECT * FROM rule_version ORDER BY id DESC LIMIT 100");
  }

  @PostMapping("/admin/rules")
  public void saveRule(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    f.saveRule(auth.admin(r, "OPERATOR").id(), b);
  }

  @PostMapping("/admin/rules/{id}/approve")
  public void approveRule(@PathVariable long id, HttpServletRequest r) {
    f.approveRule(auth.admin(r).id(), id);
  }

  @GetMapping("/admin/settlements")
  public Object settlements(HttpServletRequest r) {
    auth.admin(r, "OPERATOR", "FINANCE");
    var rows = d.all("SELECT * FROM settlement ORDER BY period DESC LIMIT 100");
    for (var s : rows)
      s.put(
          "awards",
          d.all(
              "SELECT * FROM settlement_award WHERE settlement_id=? ORDER BY rank_no", n(s, "id")));
    return rows;
  }

  @PostMapping("/admin/settlements")
  public Object settlement(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return f.monthly(auth.admin(r, "OPERATOR").id(), s(b, "period"));
  }

  @PostMapping("/admin/settlements/{id}/approve")
  public void approveSettlement(@PathVariable long id, HttpServletRequest r) {
    f.approveMonthly(auth.admin(r, "FINANCE").id(), id);
  }

  @GetMapping("/admin/reconciliation")
  public Object reconciliation(HttpServletRequest r) {
    auth.admin(r, "FINANCE");
    return Map.of(
        "batches",
        d.all("SELECT * FROM reconcile_batch ORDER BY created_at DESC LIMIT 100"),
        "issues",
        d.all("SELECT * FROM issue ORDER BY created_at DESC LIMIT 200"),
        "outbox",
        d.all("SELECT * FROM outbox WHERE state<>'DONE' ORDER BY created_at LIMIT 100"));
  }

  @PostMapping("/admin/reconciliation")
  public Object reconcile(HttpServletRequest r) {
    return jobs.reconcile(auth.admin(r, "FINANCE").id());
  }

  @PostMapping("/admin/issues/{id}/resolve")
  @Transactional
  public void resolve(
      @PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    long actor = auth.admin(r, "FINANCE").id();
    Problem.require(s(b, "reason").length() >= 4, "REASON_REQUIRED", "请填写实际处理依据，至少4字");
    d.exec("UPDATE issue SET state='RESOLVED' WHERE id=?", id);
    d.audit(actor, "ISSUE_RESOLVE", "" + id, b);
  }

  @GetMapping("/admin/accounts")
  public Object accounts(HttpServletRequest r) {
    auth.admin(r);
    return d.all("SELECT id,username,role,active FROM admin_account ORDER BY id");
  }

  @PostMapping("/admin/accounts")
  public void createAccount(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    auth.createAdmin(auth.admin(r).id(), b);
  }

  @PostMapping("/admin/accounts/{id}/active")
  @Transactional
  public void accountActive(
      @PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    long actor = auth.admin(r).id();
    Problem.require(id != actor, "SELF_DISABLE", "不能停用当前登录账号");
    var target = d.one("SELECT * FROM admin_account WHERE id=? FOR UPDATE", id);
    boolean enabled = b(b, "active");
    if (!enabled && s(target, "role").equals("ADMIN"))
      Problem.require(
          d.count("SELECT COUNT(*) FROM admin_account WHERE role='ADMIN' AND active=TRUE") > 1,
          "LAST_ADMIN",
          "不能停用最后一个管理员");
    d.exec("UPDATE admin_account SET active=? WHERE id=?", enabled, id);
    if (!enabled) d.exec("DELETE FROM session_token WHERE actor_id=? AND kind='ADMIN'", id);
    d.audit(actor, "ACCOUNT_ACTIVE", "" + id, b);
  }

  @PostMapping("/admin/accounts/{id}/reset-password")
  @Transactional
  public void resetPassword(
      @PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    long actor = auth.admin(r).id();
    Problem.require(id != actor, "SELF_RESET", "请使用个人修改密码功能");
    String password = s(b, "password");
    Problem.require(password.length() >= 12, "WEAK_PASSWORD", "新密码至少12位");
    d.one("SELECT id FROM admin_account WHERE id=? FOR UPDATE", id);
    d.exec(
        "UPDATE admin_account SET password_hash=? WHERE id=?", auth.encodePassword(password), id);
    d.exec("DELETE FROM session_token WHERE actor_id=? AND kind='ADMIN'", id);
    d.audit(actor, "ACCOUNT_PASSWORD_RESET", "" + id, Map.of());
  }

  @GetMapping("/admin/audit")
  public Object audit(HttpServletRequest r) {
    auth.admin(r, "FINANCE");
    return d.all("SELECT * FROM audit ORDER BY created_at DESC,id DESC LIMIT 500");
  }
}
