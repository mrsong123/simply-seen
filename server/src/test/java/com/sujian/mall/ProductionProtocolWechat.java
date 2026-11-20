package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Official create/query/notification shapes intentionally differ. */
final class ProductionProtocolWechat {
  static final String APP = "wx-protocol-app";
  static final String MCH = "protocol-merchant";
  final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();
  final Map<String, Map<String, Object>> refunds = new ConcurrentHashMap<>();
  final Map<String, Map<String, Object>> transfers = new ConcurrentHashMap<>();
  final Map<String, AtomicInteger> creates = new ConcurrentHashMap<>();
  final ProductionProtocolKeys keys;

  ProductionProtocolWechat(ProductionProtocolKeys keys) { this.keys = keys; }

  @SuppressWarnings("unchecked")
  Object request(String method, String path, Map<String, Object> body, Map<String, String> headers) throws Exception {
    if (path.matches("/v3/pay/transactions/(jsapi|native|h5)")) {
      assertEquals(APP, body.get("appid"));
      assertEquals(MCH, body.get("mchid"));
      assertTrue(String.valueOf(body.get("notify_url")).startsWith("https://"));
      String no = String.valueOf(body.get("out_trade_no"));
      String channel = path.substring(path.lastIndexOf('/') + 1);
      creates.computeIfAbsent(no, ignored -> new AtomicInteger()).incrementAndGet();
      var stored = new LinkedHashMap<>(body);
      stored.put("trade_type", Map.of("jsapi", "JSAPI", "native", "NATIVE", "h5", "MWEB").get(channel));
      stored.put("trade_state", "NOTPAY");
      orders.putIfAbsent(no, stored);
      return switch (channel) {
        case "native" -> Map.of("code_url", "weixin://wxpay/bizpayurl?pr=" + no);
        case "h5" -> {
          var scene = (Map<String, Object>) body.get("scene_info");
          assertEquals("192.0.2.44", scene.get("payer_client_ip"));
          yield Map.of("h5_url", "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=" + no);
        }
        default -> {
          assertNotNull(((Map<?, ?>) body.get("payer")).get("openid"));
          yield Map.of("prepay_id", "prepay-" + no);
        }
      };
    }
    if (path.startsWith("/v3/pay/transactions/out-trade-no/")) {
      String no = path.substring("/v3/pay/transactions/out-trade-no/".length()).replace("/close", "");
      if (path.endsWith("/close")) {
        assertEquals("POST", method);
        assertEquals(MCH, body.get("mchid"));
        orders.get(no).put("trade_state", "CLOSED");
        return null;
      }
      return payment(no);
    }
    if (path.equals("/v3/refund/domestic/refunds")) {
      String id = String.valueOf(body.get("out_refund_no"));
      var row = new LinkedHashMap<>(body);
      row.put("refund_id", "refund-" + id);
      row.put("status", "PROCESSING");
      refunds.put(id, row);
      return refund(id, false);
    }
    if (path.startsWith("/v3/refund/domestic/refunds/"))
      return refund(path.substring("/v3/refund/domestic/refunds/".length()), false);
    if (path.equals("/v3/fund-app/mch-transfer/transfer-bills")) {
      assertEquals(ProductionProtocolKeys.PLATFORM_SERIAL, headers.get("Wechatpay-Serial"));
      assertEquals("协议核验", keys.decryptName(String.valueOf(body.get("user_name"))));
      String id = String.valueOf(body.get("out_bill_no"));
      var row = new LinkedHashMap<>(body);
      row.put("transfer_bill_no", "transfer-" + id);
      row.put("state", "WAIT_USER_CONFIRM");
      row.put("package_info", "confirmation-package-" + id);
      transfers.put(id, row);
      // The official POST result does not contain amount/appid/openid/mch_id.
      return Map.of("out_bill_no", id, "transfer_bill_no", row.get("transfer_bill_no"),
          "create_time", Instant.now().toString(), "state", "WAIT_USER_CONFIRM",
          "package_info", row.get("package_info"));
    }
    if (path.startsWith("/v3/fund-app/mch-transfer/transfer-bills/out-bill-no/"))
      return transfer(path.substring("/v3/fund-app/mch-transfer/transfer-bills/out-bill-no/".length()), false);
    throw new IllegalArgumentException("Unexpected official protocol route");
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> payment(String no) {
    var row = Objects.requireNonNull(orders.get(no));
    var proof = new LinkedHashMap<String, Object>();
    for (String key : List.of("appid", "mchid", "out_trade_no", "amount", "trade_type", "trade_state"))
      proof.put(key, row.get(key));
    if (row.containsKey("transaction_id")) proof.put("transaction_id", row.get("transaction_id"));
    if (row.containsKey("payer")) proof.put("payer", row.get("payer"));
    return proof;
  }

  Map<String, Object> pay(String no, String openid) {
    orders.get(no).put("trade_state", "SUCCESS");
    orders.get(no).put("transaction_id", "transaction-" + no);
    orders.get(no).put("payer", Map.of("openid", openid));
    return payment(no);
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> refund(String id, boolean notification) {
    var row = Objects.requireNonNull(refunds.get(id));
    var proof = new LinkedHashMap<String, Object>();
    proof.put("out_trade_no", row.get("out_trade_no"));
    proof.put("out_refund_no", id);
    proof.put("refund_id", row.get("refund_id"));
    proof.put("transaction_id", "transaction-" + row.get("out_trade_no"));
    var amount = new LinkedHashMap<>((Map<String, Object>) row.get("amount"));
    if (notification) {
      // RefundNotification uses refund_status, not the create/query response's status.
      proof.put("refund_status", row.get("status"));
      proof.put("mchid", MCH);
      amount.remove("currency");
    } else proof.put("status", row.get("status"));
    proof.put("amount", amount);
    return proof;
  }

  Map<String, Object> transfer(String id, boolean notification) {
    var row = Objects.requireNonNull(transfers.get(id));
    var proof = new LinkedHashMap<String, Object>();
    proof.put("mch_id", MCH);
    for (String key : List.of("out_bill_no", "transfer_bill_no", "state", "transfer_amount", "openid"))
      proof.put(key, row.get(key));
    proof.put("create_time", Instant.now().toString());
    proof.put("update_time", Instant.now().toString());
    if (!notification) proof.put("appid", APP);
    // Official query/notification results do not repeat package_info.
    return proof;
  }
}
