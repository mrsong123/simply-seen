package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.job.OutboxJob;
import com.sujian.mall.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:commerce;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
      "JOB_INITIAL_DELAY_MS=3600000"
    })
class CommerceTest {
  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    Settings settings() {
      return new Settings() {
        @Override
        public String get(String key, String fallback) {
          return switch (key) {
            case "APP_DEV_MODE" -> "true";
            case "IDENTITY_REQUIRED_FOR_WITHDRAWAL" -> "false";
            case "ADMIN_BOOTSTRAP_PASSWORD" -> "test-only-password-2026";
            default -> super.get(key, fallback);
          };
        }
      };
    }
  }

  @Autowired BusinessSupport support;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  TestDatabase d;
  @Autowired CommerceService shop;
  @Autowired FinanceService f;
  @Autowired AuthService auth;
  @Autowired BootstrapService bootstrap;
  @Autowired OutboxJob jobs;
  @Autowired MaintenanceService maintenance;
  @Autowired ReconciliationService reconciliation;
  @Autowired PlatformTransactionManager tm;
  TransactionTemplate tx;

  @BeforeEach
  void reset() {
    d = new TestDatabase(jdbc, support);
    tx = new TransactionTemplate(tm);
    tx.executeWithoutResult(
        t -> {
          for (String table :
              List.of(
                  "member_phone",
                  "sms_challenge",
                  "sms_throttle",
                  "sms_cooldown",
                  "account_security_lock",
                  "member_identity",
                  "identity_attempt",
                  "reconcile_batch",
                  "settlement_award",
                  "settlement",
                  "issue",
                  "audit",
                  "idempotency",
                  "outbox",
                  "withdrawal",
                  "refund",
                  "commission",
                  "ledger",
                  "order_item",
                  "shop_order",
                  "quote",
                  "cart_item",
                  "address",
                  "session_token",
                  "member",
                  "sku",
                  "product",
                  "category",
                  "rule_version",
                  "admin_account",
                  "rate_limit")) d.exec("DELETE FROM " + table);
          bootstrap.initialize();
        });
  }

  long member(String name, String invite) {
    var login = auth.devLogin(Map.of("name", name, "inviteCode", invite));
    return n((Map<String, Object>) login.get("profile"), "id");
  }

  long address(long uid) {
    return n(
        (Map<String, Object>)
            shop.address(
                uid,
                Map.of(
                    "name",
                    "测试收货人",
                    "phone",
                    "13800138000",
                    "region",
                    "浙江省 杭州市 西湖区",
                    "detail",
                    "测试路1号",
                    "is_default",
                    true)),
        "id");
  }

  Map<String, Object> quote(long uid, long addr, int qty, int points) {
    return shop.quote(
        uid,
        Map.of(
            "items",
            List.of(Map.of("skuId", 10, "quantity", qty)),
            "addressId",
            addr,
            "pointsRequested",
            points));
  }

  Map<String, Object> order(long uid, int qty, int points) {
    long a = address(uid);
    var q = quote(uid, a, qty, points);
    return (Map<String, Object>)
        shop.create(
            uid, UUID.randomUUID().toString(), Map.of("quoteId", s(q, "quoteId"), "addressId", a));
  }

  void pay(Map<String, Object> o) {
    shop.paid(s(o, "id"), "DEV-" + s(o, "id"), n(o, "cash_due"));
    shop.benefits(s(o, "id"));
  }

  @Test
  void orderIdempotencyReservesOnceAndRejectsChangedPayload() {
    long u = member("Buyer A", "");
    long a = address(u);
    var q = quote(u, a, 1, 0);
    var body = Map.<String, Object>of("quoteId", s(q, "quoteId"), "addressId", a);
    var first = (Map<String, Object>) shop.create(u, "same-key-001", body);
    var second = (Map<String, Object>) shop.create(u, "same-key-001", body);
    assertEquals(s(first, "id"), s(second, "id"));
    assertEquals(1, d.count("SELECT held FROM sku WHERE id=10"));
    assertThrows(
        Problem.class,
        () -> shop.create(u, "same-key-001", Map.of("quoteId", "other", "addressId", a)));
    assertEquals(1, d.count("SELECT COUNT(*) FROM shop_order"));
  }

  @Test
  void cancelReleasesHeldPointsAndStockExactlyOnce() {
    long u = member("Buyer B", "");
    tx.executeWithoutResult(t -> shop.asset(u, "points", 20, "grant", null, "测试积分"));
    var o = order(u, 1, 10);
    assertEquals(10, n(auth.profile(u), "points"));
    assertEquals(10, n(auth.profile(u), "points_held"));
    shop.cancel(u, s(o, "id"));
    shop.cancel(u, s(o, "id"));
    assertEquals(20, n(auth.profile(u), "points"));
    assertEquals(0, n(auth.profile(u), "points_held"));
    assertEquals(0, d.count("SELECT held FROM sku WHERE id=10"));
  }

  @Test
  void repeatedPaymentCannotDuplicateStockOrBenefits() {
    long u = member("Buyer C", "");
    var o = order(u, 1, 0);
    for (int i = 0; i < 50; i++) pay(o);
    assertEquals(99, d.count("SELECT stock FROM sku WHERE id=10"));
    assertEquals(10, n(auth.profile(u), "points"));
    assertEquals(1, d.count("SELECT COUNT(*) FROM ledger WHERE note='有效现金消费赠积分'"));
    assertThrows(Problem.class, () -> shop.cancel(u, s(o, "id")));
    assertThrows(Problem.class, () -> shop.paid(s(o, "id"), "WRONG", 1));
  }

  @Test
  void serverPriceAndPointsLimitsAreAuthoritative() {
    long u = member("Buyer D", "");
    long a = address(u);
    var q =
        shop.quote(
            u,
            Map.of(
                "addressId",
                a,
                "items",
                List.of(Map.of("skuId", 10, "quantity", 1, "price", 1)),
                "pointsRequested",
                0));
    assertEquals(13500, n(q, "cashDueFen"));
    assertThrows(Problem.class, () -> quote(u, a, 1, 1000));
    d.exec("UPDATE sku SET price=13000 WHERE id=10");
    assertThrows(
        Problem.class,
        () -> shop.create(u, "price-test-001", Map.of("quoteId", s(q, "quoteId"), "addressId", a)));
    assertEquals(0, d.count("SELECT held FROM sku WHERE id=10"));
  }

  @Test
  void twoLevelsAreSnapshottedAndOnlyRealSaleEarnsCommission() {
    long a = member("Alice", "");
    auth.promote(a);
    long b = member("Bob", s(auth.profile(a), "invite_code"));
    auth.promote(b);
    long u = member("Carol", s(auth.profile(b), "invite_code"));
    var o = order(u, 1, 0);
    assertEquals(0, d.count("SELECT COUNT(*) FROM commission"));
    pay(o);
    var rows = d.all("SELECT * FROM commission ORDER BY level_no");
    assertEquals(2, rows.size());
    assertEquals(b, n(rows.get(0), "beneficiary_id"));
    assertEquals(a, n(rows.get(1), "beneficiary_id"));
    assertEquals(645, n(rows.get(0), "amount"));
    assertEquals(258, n(rows.get(1), "amount"));
    assertEquals(0, n(auth.profile(a), "available"));
    assertEquals(0, n(auth.profile(b), "available"));
    assertEquals(0, n(auth.profile(u), "available"));
  }

  @Test
  void commissionCannotExceedMarginOrMonthlyBudget() {
    long a = member("Alice", "");
    auth.promote(a);
    long u = member("Carol", s(auth.profile(a), "invite_code"));
    d.exec("UPDATE sku SET cost=12899 WHERE id=10");
    pay(order(u, 1, 0));
    assertEquals(0, d.count("SELECT COUNT(*) FROM commission"));
    d.exec("UPDATE sku SET cost=0 WHERE id=10");
    var rule = shop.rules();
    rule.put("commissionBudgetFen", 100L);
    d.exec("UPDATE rule_version SET config=? WHERE state='ACTIVE'", d.encode(rule));
    pay(order(u, 1, 0));
    assertTrue(d.count("SELECT COALESCE(SUM(amount),0) FROM commission") <= 100);
  }

  @Test
  void threePartialRefundsNeverExceedCashOrPoints() {
    long u = member("Buyer E", "");
    tx.executeWithoutResult(t -> shop.asset(u, "points", 30, "grant", null, "测试积分"));
    var o = order(u, 3, 10);
    pay(o);
    var item = d.one("SELECT * FROM order_item WHERE order_id=?", s(o, "id"));
    for (int i = 0; i < 3; i++) {
      var r =
          (Map<String, Object>)
              f.refund(
                  u,
                  "refund-part-" + i,
                  Map.of(
                      "orderNo",
                      s(o, "id"),
                      "reason",
                      "商品退款测试",
                      "lines",
                      List.of(Map.of("orderItemId", n(item, "id"), "quantity", 1))));
      f.reviewRefund(100, s(r, "id"), true, "");
      for (int j = 0; j < 3; j++) f.refundSuccess(s(r, "id"), "TEST-" + i);
    }
    var result = shop.order(u, s(o, "id"));
    assertEquals(n(o, "cash_due"), n(result, "cash_refunded"));
    assertEquals(10, n(result, "points_refunded"));
    assertEquals("REFUNDED", s(result, "state"));
    assertEquals(100, d.count("SELECT stock FROM sku WHERE id=10"));
    assertEquals(30, n(auth.profile(u), "points"));
    assertThrows(
        Problem.class,
        () ->
            f.refund(
                u,
                "extra-refund-1",
                Map.of(
                    "orderNo",
                    s(o, "id"),
                    "reason",
                    "测试",
                    "lines",
                    List.of(Map.of("orderItemId", n(item, "id"), "quantity", 1)))));
  }

  @Test
  void paidThenTimeoutDoesNotReleaseStock() {
    long u = member("Buyer F", "");
    var o = order(u, 1, 0);
    pay(o);
    d.exec("UPDATE shop_order SET expires_at=0 WHERE id=?", s(o, "id"));
    maintenance.maintain();
    assertEquals("PAID", s(shop.order(u, s(o, "id")), "state"));
    assertEquals(99, d.count("SELECT stock FROM sku WHERE id=10"));
  }

  @Test
  void withdrawalsRequireDifferentExecutorAndCannotOverspend() {
    long u = member("Seller", "");
    tx.executeWithoutResult(t -> shop.asset(u, "available", 3000, "grant", null, "测试佣金"));
    var r = (Map<String, Object>) f.withdraw(u, "withdraw-key-1", Map.of("amountFen", 2000));
    assertThrows(Problem.class, () -> f.withdraw(u, "withdraw-key-2", Map.of("amountFen", 2000)));
    f.reviewWithdrawal(
        100, s(r, "id"), Map.of("approve", true, "taxFen", 0, "reason", "经核对适用税务依据"));
    assertThrows(Problem.class, () -> f.executeWithdrawal(100, s(r, "id")));
    f.executeWithdrawal(101, s(r, "id"));
    f.transferResult(s(r, "id"), Map.of("state", "SUCCESS", "transfer_bill_no", "TEST"));
    f.transferResult(s(r, "id"), Map.of("state", "SUCCESS", "transfer_bill_no", "TEST"));
    assertEquals(0, n(auth.profile(u), "reserved"));
    assertEquals(1000, n(auth.profile(u), "available"));
    assertEquals(1, d.count("SELECT COUNT(*) FROM ledger WHERE event_key=?", s(r, "id") + ":paid"));
  }

  @Test
  void refundAfterCommissionPaidCreatesDebtNotNegativeBalance() {
    long a = member("Promoter", "");
    auth.promote(a);
    long u = member("Customer", s(auth.profile(a), "invite_code"));
    var o = order(u, 3, 0);
    pay(o);
    shop.ship(100, s(o, "id"), Map.of("carrier", "顺丰", "trackingNo", "SF123456789"));
    shop.receive(u, s(o, "id"));
    d.exec("UPDATE shop_order SET completed_at=? WHERE id=?", d.now() - 8 * 86400000L, s(o, "id"));
    f.settleCommissions();
    long earned = n(auth.profile(a), "available");
    assertTrue(earned > 1000);
    var wd = (Map<String, Object>) f.withdraw(a, "withdraw-earned", Map.of("amountFen", earned));
    f.reviewWithdrawal(100, s(wd, "id"), Map.of("approve", true, "taxFen", 0, "reason", "测试审核依据"));
    f.executeWithdrawal(101, s(wd, "id"));
    f.transferResult(s(wd, "id"), Map.of("state", "SUCCESS", "transfer_bill_no", "DEV"));
    var i = d.one("SELECT * FROM order_item WHERE order_id=?", s(o, "id"));
    var r =
        (Map<String, Object>)
            f.refund(
                u,
                "refund-after-paid",
                Map.of(
                    "orderNo",
                    s(o, "id"),
                    "reason",
                    "商品质量售后",
                    "lines",
                    List.of(Map.of("orderItemId", n(i, "id"), "quantity", 3))));
    f.reviewRefund(100, s(r, "id"), true, "");
    f.refundSuccess(s(r, "id"), "TEST");
    assertEquals(0, n(auth.profile(a), "available"));
    assertEquals(earned, n(auth.profile(a), "debt"));
    assertEquals(1, d.count("SELECT COUNT(*) FROM issue WHERE kind='COMMISSION_RECOVERY'"));
  }

  @Test
  void memberResourceIsolationAndRoleEnforcement() {
    long a = member("Owner", "");
    long b = member("Other", "");
    var o = order(a, 1, 0);
    assertThrows(Problem.class, () -> shop.order(b, s(o, "id")));
    var result = auth.devLogin(Map.of("name", "Owner"));
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + s(result, "token"));
    assertThrows(Problem.class, () -> auth.admin(request, "FINANCE"));
    assertEquals(a, auth.member(request).id());
  }

  @Test
  void monthlyDraftIsUniqueAndDualApprovalRequired() {
    long u = member("Monthly buyer", "");
    var o = order(u, 1, 0);
    pay(o);
    var last = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Shanghai")).minusMonths(1);
    long ts =
        last.atDay(15)
            .atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli();
    d.exec("UPDATE shop_order SET paid_at=? WHERE id=?", ts, s(o, "id"));
    var st = (Map<String, Object>) f.monthly(100, last.toString());
    assertThrows(Problem.class, () -> f.monthly(100, last.toString()));
    assertThrows(Problem.class, () -> f.approveMonthly(100, n(st, "id")));
    f.approveMonthly(101, n(st, "id"));
    assertEquals(1290, n(auth.profile(u), "available"));
    assertThrows(Problem.class, () -> f.approveMonthly(101, n(st, "id")));
  }

  @Test
  void failedLoginsRemainRateLimited() {
    for (int i = 0; i < 10; i++)
      assertThrows(
          Problem.class,
          () -> auth.adminLogin(Map.of("username", "wrong", "password", "bad"), "rate-test"));
    var ex =
        assertThrows(
            Problem.class,
            () -> auth.adminLogin(Map.of("username", "wrong", "password", "bad"), "rate-test"));
    assertEquals(429, ex.status);
  }

  @Test
  void ledgerRebuildMatchesBalances() {
    long u = member("Ledger Buyer", "");
    tx.executeWithoutResult(t -> shop.asset(u, "points", 20, "grant", null, "测试"));
    var o = order(u, 1, 5);
    pay(o);
    var batch = reconciliation.reconcile(100);
    assertEquals(0, n(batch, "diff_count"));
    d.exec("UPDATE member SET available=123 WHERE id=?", u);
    batch = reconciliation.reconcile(100);
    assertEquals(1, n(batch, "diff_count"));
  }

  @Test
  void concurrentReservationsCannotOversell() throws Exception {
    d.exec("UPDATE sku SET stock=5 WHERE id=10");
    var requests = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 12; i++) {
      long u = member("Concurrent " + i, "");
      long a = address(u);
      var q = quote(u, a, 1, 0);
      requests.add(Map.of("uid", u, "addressId", a, "quoteId", s(q, "quoteId")));
    }
    try (var pool = Executors.newFixedThreadPool(12)) {
      var start = new CountDownLatch(1);
      List<Future<Boolean>> outcomes = new ArrayList<>();
      for (var r : requests)
        outcomes.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    shop.create(
                        n(r, "uid"),
                        UUID.randomUUID().toString(),
                        Map.of("quoteId", r.get("quoteId"), "addressId", r.get("addressId")));
                    return true;
                  } catch (Problem | org.springframework.dao.DataAccessException e) {
                    return false;
                  }
                }));
      start.countDown();
      int success = 0;
      for (var future : outcomes) if (future.get(30, TimeUnit.SECONDS)) success++;
      assertEquals(5, success);
      assertEquals(5, d.count("SELECT held FROM sku WHERE id=10"));
      assertEquals(5, d.count("SELECT COUNT(*) FROM shop_order"));
    }
  }
}
