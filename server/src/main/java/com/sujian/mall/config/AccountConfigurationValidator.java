package com.sujian.mall.config;

import com.sujian.mall.common.Problem;
import java.net.URI;
import java.util.*;

public final class AccountConfigurationValidator {
  private AccountConfigurationValidator() {}
  public static void validate(Settings settings) {
    if (settings.dev()) return;
    var errors = new ArrayList<String>();
    collect(settings, errors);
    if (!errors.isEmpty()) throw new Problem(503, "ACCOUNT_CONFIGURATION", String.join("；", errors));
  }
  static void collect(Settings settings, List<String> errors) {
    if (settings.dev()) return;
    for (String prefix : List.of("SMS", "IDENTITY")) {
      if (!settings.get(prefix + "_PROVIDER", "aliyun").trim().equalsIgnoreCase("aliyun"))
        errors.add(prefix + "_PROVIDER生产环境必须为aliyun");
      for (String suffix : List.of("ACCESS_KEY_ID", "ACCESS_KEY_SECRET", "REGION_ID"))
        ConfigurationChecks.required(settings, prefix + "_" + suffix, errors);
      String name = prefix + "_API_URL";
      ConfigurationChecks.https(settings, name, errors);
      try {
        String value = settings.get(name, "");
        if (!value.isBlank() && URI.create(value).getQuery() != null) errors.add(name + "不能包含查询参数");
      } catch (IllegalArgumentException ignored) { /* Address error is already included above. */ }
    }
    ConfigurationChecks.required(settings, "SMS_SIGN_NAME", errors);
    ConfigurationChecks.required(settings, "SMS_TEMPLATE_CODE", errors);
    String key = ConfigurationChecks.required(settings, "ACCOUNT_DATA_KEY", errors);
    if (!key.isBlank()) {
      try {
        if (Base64.getDecoder().decode(key).length != 32) throw new IllegalArgumentException();
      } catch (IllegalArgumentException error) { errors.add("ACCOUNT_DATA_KEY必须为Base64编码的32字节随机密钥"); }
    }
  }
}
