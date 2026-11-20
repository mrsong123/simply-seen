package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujian.mall.config.Settings;
import com.sujian.mall.job.OutboxJob;
import com.sujian.mall.service.BootstrapService;
import com.sujian.mall.service.CommissionSettlementService;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Actual prod guards + MySQL + real HTTPS/RSA/AES/RPC, using isolated suppliers, never live money. */
@SpringBootTest(properties = {
    "APP_CONFIG_FILE=", "spring.datasource.url=${TEST_DATABASE_URL}",
    "spring.datasource.username=${DATABASE_USER}", "spring.datasource.password=${DATABASE_PASSWORD}",
    "JOB_INITIAL_DELAY_MS=3600000", "API_DOCS_ENABLED=false"})
@ActiveProfiles("prod")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(ProductionProtocolConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:mysql://[^/]+/(?:test_[A-Za-z0-9_]+|[A-Za-z0-9_]+_test)(?:[?;].*)?")
class ProductionProtocolTest {
  @Autowired MockMvc http;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource source;
  @Autowired BootstrapService bootstrap;
  @Autowired CommissionSettlementService commissions;
  @Autowired OutboxJob jobs;
  @Autowired Settings settings;
  ProductionProtocolApi api;
  final ProductionProtocolProvider supplier = ProductionProtocolConfiguration.PROVIDER;

  @BeforeEach
  void cleanDedicatedDatabase() throws Exception {
    String url = System.getenv("TEST_DATABASE_URL");
    assertTrue(url.matches("jdbc:mysql://[^/]+/(?:test_[A-Za-z0-9_]+|[A-Za-z0-9_]+_test)(?:[?;].*)?"),
        "Protocol acceptance must target a disposable test database");
    try (Connection connection = source.getConnection();
        var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
      while (tables.next()) {
        String table = tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
        if (!Set.of("flyway_schema_history", "id_allocator", "system_guard").contains(table)) {
          assertTrue(table.matches("[a-z_]+"));
          jdbc.update("DELETE FROM " + table);
        }
      }
    }
    bootstrap.initialize();
    api = new ProductionProtocolApi(http, json);
    assertFalse(settings.dev());
    assertTrue(settings.identityRequiredForWithdrawal());
  }

  @AfterAll static void stopSupplier() { ProductionProtocolConfiguration.PROVIDER.close(); }

  @Test
  @SuppressWarnings("unchecked")
  void productionAuthenticationPaymentsRefundsDistributionAndPayoutUseOfficialProtocols() throws Exception {
    String admin = api.admin("admin", ProductionProtocolConfiguration.PASSWORD);
    String finance = createAdmin(admin, "proto-finance", "FINANCE");
    String operator = createAdmin(admin, "proto-operator", "OPERATOR");
    String executor = createAdmin(admin, "proto-executor", "ADMIN");
    long sku = createCatalog(admin);
    var ancestor = api.phone("13300001001", "", true);
    var promoter = api.phone("13300001002", ancestor.invite(), true);
    var buyer = api.phone("13300001003", promoter.invite(), false);
    api.call("POST", "/me/wechat", Map.of("code", "wx-b"), promoter.token(), 200);
    api.call("POST", "/auth/dev", Map.of("name", "DisabledDevelopmentUser"), null, 404);
    api.call("GET", "/admin/accounts", null, buyer.token(), 403);
    api.call("GET", "/admin/withdrawals", null, operator, 403);

    var nativeOrder = order(buyer, sku);
    String no = s(nativeOrder, "id");
    api.call("GET", "/orders/" + no, null, ancestor.token(), 404);
    var nativePrepay = api.map("POST", "/orders/" + no + "/prepay", Map.of("channel", "NATIVE"), buyer.token());
    assertEquals("NATIVE", nativePrepay.get("mode"));
    assertTrue(s(nativePrepay, "codeUrl").startsWith("weixin://wxpay/"));
    api.map("POST", "/orders/" + no + "/prepay", Map.of("channel", "NATIVE"), buyer.token());
    assertEquals(1, supplier.wechat.creates.get(no).get(), "Repeated prepay reuses one intent");
    var proof = supplier.wechat.pay(no, "openid-native-paying-friend");
    api.callback("pay", proof, false);
    assertEquals("CREATED", api.map("GET", "/orders/" + no, null, buyer.token()).get("state"));
    api.callback("pay", proof, true);
    api.callback("pay", proof, true);
    jobs.run();
    assertEquals(2L, number("SELECT COUNT(*) FROM commission WHERE order_id=?", no));
    assertEquals(7000L, number("SELECT SUM(amount) FROM commission WHERE order_id=?", no));
    shipReceiveAndAge(admin, buyer, no);
    commissions.settleCommissions();
    assertEquals(5000, balance(promoter, "available"));
    assertEquals(2000, balance(ancestor, "available"));

    var blocked = (Map<String, Object>) api.call("POST", "/me/withdrawals", Map.of("amountFen", 1000), promoter.token(), 409);
    assertEquals("IDENTITY_REQUIRED", blocked.get("code"));
    assertEquals(0, balance(promoter, "reserved"));
    var identity = api.map("POST", "/me/identity", Map.of("name", "协议核验", "idNumber", "11010519491231002X", "accepted", true), promoter.token());
    assertEquals("VERIFIED", identity.get("status"));
    assertEquals(false, identity.get("development"));
    assertFalse(String.valueOf(identity).contains("11010519491231002X"));
    String withdrawal = s(api.map("POST", "/me/withdrawals", Map.of("amountFen", 1000), promoter.token()), "id");
    api.call("POST", "/admin/withdrawals/" + withdrawal + "/review",
        Map.of("approve", true, "taxFen", 100, "reason", "核实身份与税款"), finance, 200);
    api.call("POST", "/admin/withdrawals/" + withdrawal + "/execute", null, finance, 403);
    api.call("POST", "/admin/withdrawals/" + withdrawal + "/execute", null, executor, 200);
    var pending = api.rows("/me/withdrawals", promoter.token()).getFirst();
    assertEquals("CONFIRMING", pending.get("state"));
    assertFalse(s(pending, "package_info").isBlank());
    String confirmationPackage = s(pending, "package_info");
    api.call("POST", "/admin/withdrawals/" + withdrawal + "/query", null, finance, 200);
    assertEquals(confirmationPackage, s(api.rows("/me/withdrawals", promoter.token()).getFirst(), "package_info"));
    supplier.wechat.transfers.get(withdrawal).put("state", "SUCCESS");
    var transferProof = supplier.wechat.transfer(withdrawal, true);
    assertFalse(transferProof.containsKey("appid"));
    api.callback("transfer", transferProof, true);
    api.callback("transfer", transferProof, true);
    assertEquals(0, balance(promoter, "reserved"));
    assertEquals(4000, balance(promoter, "available"));

    var detail = api.map("GET", "/orders/" + no, null, buyer.token());
    assertFalse(String.valueOf(detail).contains("openid-native-paying-friend"));
    var item = ((List<Map<String, Object>>) detail.get("items")).getFirst();
    String refund = s(api.map("POST", "/refunds", Map.of("orderNo", no, "reason", "完整协议售后验收",
        "lines", List.of(Map.of("orderItemId", item.get("id"), "quantity", 1))), buyer.token()), "id");
    api.call("POST", "/admin/refunds/" + refund + "/review", Map.of("approve", true, "reason", "原单与实付核验通过"), finance, 200);
    assertEquals("PROCESSING", supplier.wechat.refunds.get(refund).get("status"));
    supplier.wechat.refunds.get(refund).put("status", "SUCCESS");
    var refundProof = supplier.wechat.refund(refund, true);
    assertTrue(refundProof.containsKey("refund_status"));
    assertFalse(refundProof.containsKey("status"));
    var wrongRefundMerchant = new LinkedHashMap<>(refundProof);
    wrongRefundMerchant.put("mchid", "another-merchant");
    api.callback("refund", wrongRefundMerchant, true, 409);
    var wrongRefundTransaction = new LinkedHashMap<>(refundProof);
    wrongRefundTransaction.put("transaction_id", "another-transaction");
    api.callback("refund", wrongRefundTransaction, true, 409);
    assertEquals("COMPLETED", api.map("GET", "/orders/" + no, null, buyer.token()).get("state"));
    api.callback("refund", refundProof, true);
    api.callback("refund", refundProof, true);
    assertEquals("REFUNDED", api.map("GET", "/orders/" + no, null, buyer.token()).get("state"));
    assertEquals(0L, number("SELECT SUM(amount) FROM commission WHERE order_id=?", no));
    assertEquals(0, balance(promoter, "available"));
    assertEquals(1000, balance(promoter, "debt"), "Already paid commission becomes recoverable debt");

    // A separate mini-program transaction exercises true JSAPI signing and its payer binding.
    api.call("POST", "/me/wechat", Map.of("code", "wx-c"), buyer.token(), 200);
    String miniNo = s(order(buyer, sku), "id");
    var mini = api.map("POST", "/orders/" + miniNo + "/prepay", null, buyer.token());
    assertTrue(s(mini, "package").startsWith("prepay_id="));
    assertTrue(ProductionProtocolKeys.verify(supplier.keys.merchant.getPublic(),
        ProductionProtocolWechat.APP + "\n" + mini.get("timeStamp") + "\n" + mini.get("nonceStr") + "\n" + mini.get("package") + "\n", s(mini, "paySign")));
    var miniProof = supplier.wechat.pay(miniNo, "openid-wx-c");
    var wrongPayer = new LinkedHashMap<>(miniProof);
    wrongPayer.put("payer", Map.of("openid", "openid-someone-else"));
    api.callback("pay", wrongPayer, true, 409);
    assertEquals("CREATED", api.map("GET", "/orders/" + miniNo, null, buyer.token()).get("state"));
    api.callback("pay", miniProof, true);
    jobs.run();
    shipReceiveAndAge(admin, buyer, miniNo);
    commissions.settleCommissions();
    assertEquals(0, balance(promoter, "debt"));
    assertEquals(4000, balance(promoter, "available"));

    // Official H5 redirect is returned and the uncompleted order can be closed cleanly.
    String h5No = s(order(buyer, sku), "id");
    var h5 = api.map("POST", "/orders/" + h5No + "/prepay", Map.of("channel", "H5"), buyer.token());
    assertEquals("H5", h5.get("mode"));
    assertTrue(s(h5, "h5Url").startsWith("https://wx.tenpay.com/"));
    assertTrue(s(h5, "h5Url").contains("redirect_url="));
    api.call("POST", "/orders/" + h5No + "/cancel", null, buyer.token(), 200);
    assertEquals("CLOSED", api.map("GET", "/orders/" + h5No, null, buyer.token()).get("state"));
    assertEquals(0L, number("SELECT SUM(held) FROM sku"));
    String paidH5 = s(order(buyer, sku), "id");
    api.map("POST", "/orders/" + paidH5 + "/prepay", Map.of("channel", "H5"), buyer.token());
    api.callback("pay", supplier.wechat.pay(paidH5, "openid-h5-payer"), true);
    jobs.run();
    assertEquals("PAID", api.map("GET", "/orders/" + paidH5, null, buyer.token()).get("state"));

    String month = YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1).toString();
    long settlement = n(api.map("POST", "/admin/settlements", Map.of("period", month), admin), "id");
    api.call("POST", "/admin/settlements/" + settlement + "/approve", null, admin, 409);
    api.call("POST", "/admin/settlements/" + settlement + "/approve", null, finance, 200);
    assertEquals(10000, balance(buyer, "available"));
    var reconciliation = api.map("POST", "/admin/reconciliation", null, finance);
    assertEquals(0, n(reconciliation, "diff_count"));
    assertTrue(supplier.rpcRequests.get() >= 4);
    assertTrue(supplier.signedRequests.get() >= 10);
  }

  private String createAdmin(String root, String username, String role) throws Exception {
    api.call("POST", "/admin/accounts", Map.of("username", username, "password", ProductionProtocolConfiguration.PASSWORD, "role", role), root, 200);
    return api.admin(username, ProductionProtocolConfiguration.PASSWORD);
  }

  @SuppressWarnings("unchecked")
  private long createCatalog(String admin) throws Exception {
    long category = n(api.map("POST", "/admin/categories", Map.of("name", "协议验收分类", "sort_no", 1), admin), "id");
    var body = new LinkedHashMap<String, Object>();
    body.put("category_id", category); body.put("name", "协议验收商品");
    body.put("subtitle", "受控生产协议验收"); body.put("description", "仅测试数据，不是真实销售商品");
    body.put("image", "https://assets.example.test/product.png"); body.put("color", "#eeeeee");
    body.put("status", "ON_SALE"); body.put("featured", true);
    body.put("points_percent", 20); body.put("commission_percent", 7);
    body.put("skus", List.of(Map.of("name", "标准款", "code", "PROTOCOL-ONE", "price", 100000, "cost", 20000, "stock", 20)));
    long id = n(api.map("POST", "/admin/products", body, admin), "id");
    var product = api.rows("/admin/products", admin).stream().filter(row -> n(row, "id") == id).findFirst().orElseThrow();
    return n(((List<Map<String, Object>>) product.get("skus")).getFirst(), "id");
  }

  private Map<String, Object> order(ProductionProtocolApi.Member member, long sku) throws Exception {
    long address = n(api.map("POST", "/addresses", Map.of("name", "协议收货人", "phone", "13300001003", "region", "浙江省 杭州市 西湖区", "detail", "协议测试地址1号", "is_default", true), member.token()), "id");
    var quote = api.map("POST", "/checkout/quote", Map.of("items", List.of(Map.of("skuId", sku, "quantity", 1)), "addressId", address, "pointsRequested", 0), member.token());
    return api.map("POST", "/orders", Map.of("quoteId", quote.get("quoteId"), "addressId", address), member.token());
  }

  private void shipReceiveAndAge(String admin, ProductionProtocolApi.Member buyer, String no) throws Exception {
    api.call("POST", "/admin/orders/" + no + "/ship", Map.of("carrier", "协议物流", "trackingNo", "PROTOCOL123456"), admin, 200);
    api.call("POST", "/orders/" + no + "/receive", null, buyer.token(), 200);
    long old = YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1).atDay(3)
        .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    // Clock fixture only: avoid waiting seven days or a calendar month in an integration test.
    jdbc.update("UPDATE shop_order SET completed_at=?,paid_at=? WHERE id=?", old, old, no);
  }

  private long balance(ProductionProtocolApi.Member member, String field) throws Exception {
    return n(api.map("GET", "/me", null, member.token()), field);
  }

  private long number(String sql, Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args)); }
}
