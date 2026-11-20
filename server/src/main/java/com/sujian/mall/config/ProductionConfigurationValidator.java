package com.sujian.mall.config;

import com.sujian.mall.common.Problem;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigurationValidator {
  private final Settings settings;
  public ProductionConfigurationValidator(Settings settings) { this.settings = settings; }

  public void validate() {
    if (settings.dev()) return;
    var errors = new ArrayList<String>();
    AccountConfigurationValidator.collect(settings, errors);
    String database = ConfigurationChecks.required(settings, "DATABASE_URL", errors);
    if (!database.isBlank() && !database.startsWith("jdbc:mysql:")) errors.add("生产环境DATABASE_URL必须使用MySQL");
    if (database.matches("(?is).*[?&;](user|password)=.*"))
      errors.add("DATABASE_URL不能内嵌账号密码，请使用DATABASE_USER/PASSWORD或对应_FILE");
    for (String name : List.of("DATABASE_USER", "DATABASE_PASSWORD", "WX_APP_ID", "WX_APP_SECRET", "WX_MCH_ID", "WX_MERCHANT_SERIAL",
        "WX_PLATFORM_SERIAL", "SUPPORT_CONTACT", "PRIVACY_CONTACT")) ConfigurationChecks.required(settings, name, errors);
    for (String name : List.of("WX_LOGIN_URL", "WX_PAY_BASE_URL", "WX_PAY_NOTIFY_URL", "WX_REFUND_NOTIFY_URL", "PUBLIC_STORE_URL"))
      ConfigurationChecks.https(settings, name, errors);
    try {
      var store = java.net.URI.create(settings.get("PUBLIC_STORE_URL", ""));
      if (store.getPath() != null && !store.getPath().isEmpty() && !store.getPath().equals("/") || store.getQuery() != null)
        errors.add("PUBLIC_STORE_URL应为不带子路径和查询参数的站点根地址");
    } catch (IllegalArgumentException ignored) { /* Included in the HTTPS validation. */ }
    String apiKey = ConfigurationChecks.required(settings, "WX_API_V3_KEY", errors);
    if (!apiKey.isBlank() && apiKey.getBytes(StandardCharsets.UTF_8).length != 32) errors.add("WX_API_V3_KEY必须为32字节");
    RsaKeyConfiguration.validate(settings, errors);
    String mode = settings.get("WX_WEB_PAYMENT_MODE", "native").trim().toLowerCase(Locale.ROOT);
    if (!Set.of("native", "h5").contains(mode)) errors.add("WX_WEB_PAYMENT_MODE只允许native或h5");
    ConfigurationChecks.integer(settings, "SESSION_HOURS", 168, 1, 720, errors);
    ConfigurationChecks.integer(settings, "ORDER_EXPIRE_MINUTES", 15, 1, 120, errors);
    ConfigurationChecks.integer(settings, "SMS_DAILY_LIMIT", 1000, 1, 1000000, errors);
    if (settings.enabled("TRANSFER_ENABLED")) {
      for (String name : List.of("WX_TRANSFER_BASE_URL", "WX_TRANSFER_NOTIFY_URL")) ConfigurationChecks.https(settings, name, errors);
      for (String name : List.of("WX_TRANSFER_SCENE_ID", "WX_TRANSFER_PERCEPTION", "WX_TRANSFER_REPORT_INFOS"))
        ConfigurationChecks.required(settings, name, errors);
      String reports = settings.get("WX_TRANSFER_REPORT_INFOS", "");
      try {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(reports);
        if (!json.isArray() || json.isEmpty()) throw new IllegalArgumentException();
        for (var item : json)
          if (!item.path("info_type").isTextual() || item.path("info_type").asText().isBlank()
              || !item.path("info_content").isTextual() || item.path("info_content").asText().isBlank()) throw new IllegalArgumentException();
      } catch (Exception error) { errors.add("WX_TRANSFER_REPORT_INFOS必须为包含info_type和info_content的非空JSON数组"); }
    }
    if (!settings.get("LOGISTICS_QUERY_URL", "").isBlank()) {
      ConfigurationChecks.https(settings, "LOGISTICS_QUERY_URL", errors);
      for (String name : List.of("LOGISTICS_CUSTOMER", "LOGISTICS_KEY", "LOGISTICS_CARRIER_CODES")) ConfigurationChecks.required(settings, name, errors);
      try {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(settings.get("LOGISTICS_CARRIER_CODES", ""));
        if (!json.isObject() || json.isEmpty()) throw new IllegalArgumentException();
      } catch (Exception error) { errors.add("LOGISTICS_CARRIER_CODES必须为非空JSON对象"); }
    }
    if (!errors.isEmpty()) throw new Problem(503, "PRODUCTION_CONFIGURATION", "生产配置未通过：" + String.join("；", errors));
  }
}
