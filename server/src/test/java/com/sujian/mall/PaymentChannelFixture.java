package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;
import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.service.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class PaymentChannelFixture {
  static class ChannelSettings extends Settings {
    volatile boolean development = true;
    @Override public String get(String key, String fallback) {
      return switch (key) {
        case "APP_DEV_MODE" -> String.valueOf(development);
        case "ADMIN_BOOTSTRAP_PASSWORD" -> "payment-test-only-password";
        case "WX_APP_ID" -> "payment-unit-app";
        case "WX_MCH_ID" -> "payment-unit-merchant";
        case "IDENTITY_REQUIRED_FOR_WITHDRAWAL" -> "false";
        default -> fallback;
      };
    }
  }
  static class ChannelWechat extends WechatClient {
    final AtomicInteger calls = new AtomicInteger(), queries = new AtomicInteger();
    final Map<String, Map<String, Object>> evidence = new ConcurrentHashMap<>();
    volatile boolean failNext;
    ChannelWechat(Settings settings, JsonCodec json) { super(settings, json); }
    @Override public Map<String, Object> prepay(Map<String, Object> order, String openid, String channel, String ip) {
      calls.incrementAndGet();
      evidence.putIfAbsent(s(order, "id"), Map.of("out_trade_no", s(order, "id"), "trade_state", "NOTPAY",
          "mchid", "payment-unit-merchant", "appid", "payment-unit-app"));
      if (failNext) { failNext = false; throw new Problem(503, "CHANNEL_UNKNOWN", "unit-timeout"); }
      try { Thread.sleep(100); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
      return Map.of("mode", channel, "orderNo", s(order, "id"), "codeUrl", "weixin://wxpay/bizpayurl?pr=test",
          "expiresAt", n(order, "expires_at"));
    }
    @Override public Map<String, Object> queryPayment(String no) {
      queries.incrementAndGet();
      if (!evidence.containsKey(no)) throw new Problem(502, "WX_ORDER_NOT_EXIST", "unit-missing");
      return evidence.get(no);
    }
    @Override public void close(String no) {
      evidence.put(no, Map.of("out_trade_no", no, "trade_state", "CLOSED",
          "mchid", "payment-unit-merchant", "appid", "payment-unit-app"));
    }
  }
  @TestConfiguration static class Config {
    @Bean @Primary ChannelSettings channelSettings() { return new ChannelSettings(); }
    @Bean @Primary ChannelWechat channelWechat(Settings settings, JsonCodec json) { return new ChannelWechat(settings, json); }
  }
  @Autowired JdbcTemplate jdbc;
  @Autowired BusinessSupport support;
  @Autowired CommerceService shop;
  @Autowired AuthService auth;
  @Autowired BootstrapService bootstrap;
  @Autowired ChannelSettings settings;
  @Autowired ChannelWechat channel;
  @Autowired com.sujian.mall.service.impl.PrepaymentTransactions prepayments;
  TestDatabase database;

  @BeforeEach void resetPayments() {
    settings.development = true;
    database = new TestDatabase(jdbc, support);
    for (String table : List.of("payment_session", "identity_attempt", "member_identity", "member_phone",
        "sms_challenge", "sms_throttle", "sms_cooldown", "account_security_lock", "reconcile_batch",
        "settlement_award", "settlement", "issue", "audit", "idempotency", "outbox", "withdrawal",
        "refund", "commission", "ledger", "order_item", "shop_order", "quote", "cart_item", "address",
        "session_token", "member", "sku", "product", "category", "rule_version", "admin_account", "rate_limit"))
      database.exec("DELETE FROM " + table);
    bootstrap.initialize(); channel.calls.set(0); channel.queries.set(0); channel.evidence.clear(); channel.failNext = false;
  }

  @SuppressWarnings("unchecked") Map<String, Object> createOrder() {
    long member = n((Map<String, Object>) auth.devLogin(Map.of("name", "Payment Buyer " + UUID.randomUUID().toString().substring(0, 8))).get("profile"), "id");
    var address = (Map<String, Object>) shop.address(member, Map.of("name", "支付验收", "phone", "13800138000",
        "region", "浙江省 杭州市 西湖区", "detail", "支付测试路1号", "is_default", true));
    var quote = shop.quote(member, Map.of("items", List.of(Map.of("skuId", 10, "quantity", 1)),
        "addressId", n(address, "id"), "pointsRequested", 0));
    var order = (Map<String, Object>) shop.create(member, "payment-" + UUID.randomUUID(),
        Map.of("quoteId", s(quote, "quoteId"), "addressId", n(address, "id")));
    settings.development = false;
    return order;
  }

  Map<String, Object> successfulEvidence(Map<String, Object> order, String tradeType, String payer) {
    return Map.of("appid", "payment-unit-app", "mchid", "payment-unit-merchant", "trade_state", "SUCCESS",
        "trade_type", tradeType, "out_trade_no", s(order, "id"), "transaction_id", "wx-transaction-" + s(order, "id"),
        "payer", Map.of("openid", payer), "amount", Map.of("total", n(order, "cash_due"), "currency", "CNY"));
  }
}
