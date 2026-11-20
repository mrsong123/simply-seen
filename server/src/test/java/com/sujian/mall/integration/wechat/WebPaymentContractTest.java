package com.sujian.mall.integration.wechat;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

class WebPaymentContractTest {
  Map<String, String> values;
  JsonCodec json = new JsonCodec(new ObjectMapper());
  Settings settings;
  CapturingWechat api;
  Map<String, Object> order;

  class CapturingWechat extends WechatClient {
    Map<String, Object> result, body;
    String endpoint;
    CapturingWechat() { super(settings, json); }
    @Override public Map<String, Object> request(String method, String uri, String payload, boolean signed) {
      assertEquals("POST", method); assertTrue(signed); endpoint = uri; body = json.decode(payload); return result;
    }
    @Override String sign(String input) { return "unit-signature"; }
  }

  @BeforeEach void setup() {
    values = new HashMap<>(Map.of("WX_APP_ID", "unit-app", "WX_MCH_ID", "unit-merchant",
        "WX_PAY_BASE_URL", "https://payments.example", "WX_PAY_NOTIFY_URL", "https://shop.example/api/v1/callbacks/wechat/pay",
        "PUBLIC_STORE_URL", "https://shop.example"));
    settings = new Settings() { @Override public String get(String name, String fallback) { return values.getOrDefault(name, fallback); } };
    api = new CapturingWechat();
    order = Map.of("id", "SJ123", "cash_due", 13500L, "expires_at", System.currentTimeMillis() + 600000);
  }

  @Test void nativeRequestDoesNotRequireOrLeakMemberOpenid() {
    api.result = Map.of("code_url", "weixin://wxpay/bizpayurl?pr=unit-code");
    var response = api.prepay(order, "", "NATIVE", "");
    assertEquals("https://payments.example/v3/pay/transactions/native", api.endpoint);
    assertFalse(api.body.containsKey("payer")); assertEquals(13500L, ((Number) ((Map<?, ?>) api.body.get("amount")).get("total")).longValue());
    assertEquals("SJ123", api.body.get("out_trade_no")); assertTrue(api.body.containsKey("time_expire"));
    assertEquals("NATIVE", response.get("mode")); assertEquals(api.result.get("code_url"), response.get("codeUrl"));
    assertEquals(order.get("expires_at"), response.get("expiresAt"));
  }

  @Test void h5UsesTrustedClientIpAndOnlyConfiguredStoreReturnPath() {
    values.put("WX_H5_APP_ID", "unit-web-app");
    values.put("WX_H5_APP_NAME", "素见好物测试商城");
    api.result = Map.of("h5_url", "https://wx.tenpay.com/cgi-bin/checkmweb?prepay_id=unit");
    var response = api.prepay(order, "", "H5", "198.51.100.11");
    assertTrue(api.endpoint.endsWith("/h5")); assertFalse(api.body.containsKey("payer"));
    assertEquals("unit-web-app", api.body.get("appid"));
    assertEquals("198.51.100.11", ((Map<?, ?>) api.body.get("scene_info")).get("payer_client_ip"));
    assertEquals(Map.of("type", "Wap", "app_name", "素见好物测试商城", "app_url", "https://shop.example"),
        ((Map<?, ?>) api.body.get("scene_info")).get("h5_info"));
    String url = response.get("h5Url").toString();
    assertTrue(url.startsWith("https://wx.tenpay.com/"));
    assertEquals("https://shop.example/orders/SJ123", URLDecoder.decode(url.substring(url.indexOf("redirect_url=") + 13), StandardCharsets.UTF_8));
    values.put("WX_H5_APP_NAME", "");
    api.prepay(order, "", "H5", "198.51.100.11");
    var defaultInfo = (Map<?, ?>) ((Map<?, ?>) api.body.get("scene_info")).get("h5_info");
    assertEquals("素见好物", defaultInfo.get("app_name"));
  }

  @Test void untrustedH5LocationAndInvalidClientIpCannotBeReturned() {
    for (String location : List.of("http://wx.tenpay.com/pay", "https://evil.example/pay", "https://user@wx.tenpay.com/pay",
        "https://wx.tenpay.com:8443/pay", "https://wx.tenpay.com/pay#fragment", "https://wx.tenpay.com/pay?redirect_url=https://evil.example")) {
      api.result = Map.of("h5_url", location);
      assertThrows(Problem.class, () -> api.prepay(order, "", "H5", "198.51.100.11"));
    }
    assertThrows(Problem.class, () -> api.prepay(order, "", "H5", "proxy-supplied-hostname"));
  }

  @Test void invalidQrAndMissingJsapiTokenFailClosed() {
    api.result = Map.of("code_url", "https://evil.example/phishing");
    assertThrows(Problem.class, () -> api.prepay(order, "", "NATIVE", ""));
    api.result = Map.of();
    assertThrows(Problem.class, () -> api.prepay(order, "wx-member", "MINI_PROGRAM", ""));
  }

  @Test void miniAndRenewedSignaturesKeepNativePaymentParameterContract() {
    api.result = Map.of("prepay_id", "wx-unit-prepay");
    var response = api.prepay(order, "wx-member", "MINI_PROGRAM", "");
    assertTrue(api.endpoint.endsWith("/jsapi"));
    assertEquals(Map.of("openid", "wx-member"), api.body.get("payer"));
    assertEquals("prepay_id=wx-unit-prepay", response.get("package"));
    assertEquals("RSA", response.get("signType"));
    assertNotEquals(response.get("nonceStr"), api.refreshMiniPrepay(response).get("nonceStr"));
  }
}
