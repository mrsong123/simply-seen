package com.sujian.mall;

import com.sujian.mall.common.JsonCodec;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.aliyun.AliyunRpcTransport;
import com.sujian.mall.integration.wechat.WechatClient;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test-only wiring changes HTTP client trust/proxy, never business or protocol verification. */
@TestConfiguration
class ProductionProtocolConfiguration {
  static final ProductionProtocolProvider PROVIDER = create();
  static final String PASSWORD = "protocol-only-admin-password";

  private static ProductionProtocolProvider create() {
    try { return new ProductionProtocolProvider(); }
    catch (Exception error) { throw new ExceptionInInitializerError(error); }
  }

  @Bean @Primary
  Settings productionProtocolSettings() {
    var values = new HashMap<String, String>();
    values.put("APP_DEV_MODE", "false");
    values.put("DATABASE_URL", System.getenv("TEST_DATABASE_URL"));
    values.put("DATABASE_USER", System.getenv("DATABASE_USER"));
    values.put("DATABASE_PASSWORD", System.getenv("DATABASE_PASSWORD"));
    values.put("ADMIN_BOOTSTRAP_USER", "admin");
    values.put("ADMIN_BOOTSTRAP_PASSWORD", PASSWORD);
    values.put("ACCOUNT_DATA_KEY", Base64.getEncoder().encodeToString(new byte[32]));
    values.put("SUPPORT_CONTACT", "protocol-support@example.test");
    values.put("PRIVACY_CONTACT", "protocol-privacy@example.test");
    values.put("PUBLIC_STORE_URL", "https://store.protocol.test");
    values.put("WX_APP_ID", ProductionProtocolWechat.APP);
    values.put("WX_APP_SECRET", "protocol-app-secret");
    values.put("WX_MCH_ID", ProductionProtocolWechat.MCH);
    values.put("WX_MERCHANT_SERIAL", "PROTOCOL_MERCHANT_SERIAL");
    values.put("WX_PLATFORM_SERIAL", ProductionProtocolKeys.PLATFORM_SERIAL);
    values.put("WX_PRIVATE_KEY_PATH", PROVIDER.keys.directory.resolve("merchant.pem").toString());
    values.put("WX_PLATFORM_PUBLIC_KEY_PATH", PROVIDER.keys.directory.resolve("platform.pem").toString());
    values.put("WX_API_V3_KEY", ProductionProtocolKeys.V3_KEY);
    values.put("WX_LOGIN_URL", PROVIDER.origin() + "/login");
    values.put("WX_PAY_BASE_URL", PROVIDER.origin());
    values.put("WX_TRANSFER_BASE_URL", PROVIDER.origin());
    values.put("WX_WEB_PAYMENT_MODE", "native");
    for (String kind : List.of("PAY", "REFUND", "TRANSFER"))
      values.put("WX_" + kind + "_NOTIFY_URL", "https://store.protocol.test/api/v1/callbacks/wechat/" + kind.toLowerCase(Locale.ROOT));
    values.put("WX_TRANSFER_SCENE_ID", "1001");
    values.put("WX_TRANSFER_PERCEPTION", "商品推广佣金");
    values.put("WX_TRANSFER_REPORT_INFOS", "[{\"info_type\":\"活动名称\",\"info_content\":\"协议验收\"},{\"info_type\":\"奖励说明\",\"info_content\":\"商品推广佣金\"}]");
    for (String feature : List.of("DISTRIBUTION_ENABLED", "TRANSFER_ENABLED", "MONTHLY_ENABLED")) values.put(feature, "true");
    for (String prefix : List.of("SMS", "IDENTITY")) {
      values.put(prefix + "_PROVIDER", "aliyun");
      values.put(prefix + "_API_URL", PROVIDER.origin() + "/");
      values.put(prefix + "_ACCESS_KEY_ID", ProductionProtocolProvider.ACCESS_ID);
      values.put(prefix + "_ACCESS_KEY_SECRET", ProductionProtocolProvider.ACCESS_SECRET);
      values.put(prefix + "_REGION_ID", "cn-hangzhou");
    }
    values.put("SMS_SIGN_NAME", "协议短信");
    values.put("SMS_TEMPLATE_CODE", "PROTOCOL_TEMPLATE");
    return new Settings() {
      @Override public String get(String name, String fallback) {
        return Optional.ofNullable(values.get(name)).orElse(fallback);
      }
      @Override public boolean dev() { return false; }
    };
  }

  @Bean @Primary
  WechatClient productionProtocolWechatClient(Settings settings, JsonCodec codec) {
    return new WechatClient(settings, codec) {
      @Override protected HttpClient client() { return PROVIDER.client; }
    };
  }

  @Bean @Primary
  AliyunRpcTransport productionProtocolRpcTransport() {
    return (endpoint, form) -> {
      try {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build();
        var response = PROVIDER.client.send(request, HttpResponse.BodyHandlers.ofString());
        return new AliyunRpcTransport.Response(response.statusCode(), response.body());
      } catch (Exception failure) {
        throw new IllegalStateException("Isolated HTTPS protocol supplier failed", failure);
      }
    };
  }
}
