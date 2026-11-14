package com.sujian.mall.config;

import com.sujian.mall.common.Problem;
import java.net.URI;
import java.util.Base64;
import java.util.List;

/** Validate real SMS/identity configuration before accepting production traffic. */
public final class AccountConfigurationValidator {
  private AccountConfigurationValidator() {}

  public static void validate(Settings settings) {
    if (settings.dev()) return;
    for (String prefix : List.of("SMS", "IDENTITY")) {
      Problem.require(
          settings.get(prefix + "_PROVIDER", "aliyun").equals("aliyun"),
          "ACCOUNT_PROVIDER_CONFIGURATION",
          "生产环境必须使用真实阿里云短信和实名认证服务");
      for (String suffix : List.of("ACCESS_KEY_ID", "ACCESS_KEY_SECRET", "REGION_ID"))
        settings.required(prefix + "_" + suffix);
      secureEndpoint(settings.required(prefix + "_API_URL"));
    }
    settings.required("SMS_SIGN_NAME");
    settings.required("SMS_TEMPLATE_CODE");
    validateDataKey(settings.required("ACCOUNT_DATA_KEY"));
  }

  private static void secureEndpoint(String value) {
    try {
      URI endpoint = URI.create(value);
      Problem.require(
          "https".equals(endpoint.getScheme())
              && endpoint.getHost() != null
              && endpoint.getUserInfo() == null
              && endpoint.getFragment() == null
              && endpoint.getQuery() == null,
          "INSECURE_ENDPOINT",
          "短信和实名认证接口必须为不含用户信息、查询参数及片段的HTTPS地址");
    } catch (IllegalArgumentException error) {
      throw new Problem(503, "INSECURE_ENDPOINT", "短信或实名认证接口地址无效");
    }
  }

  private static void validateDataKey(String value) {
    try {
      Problem.require(
          Base64.getDecoder().decode(value).length == 32,
          "ACCOUNT_KEY_CONFIGURATION",
          "ACCOUNT_DATA_KEY必须为Base64编码的32字节随机密钥");
    } catch (IllegalArgumentException error) {
      throw new Problem(503, "ACCOUNT_KEY_CONFIGURATION", "ACCOUNT_DATA_KEY编码无效");
    }
  }
}
