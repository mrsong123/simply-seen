package com.sujian.mall.config;

import com.sujian.mall.common.Problem;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigurationValidator {
  private final Settings settings;

  public ProductionConfigurationValidator(Settings settings) {
    this.settings = settings;
  }

  public void validate() {
    if (settings.dev()) return;
    AccountConfigurationValidator.validate(settings);
    Problem.require(
        settings.required("DATABASE_URL").startsWith("jdbc:mysql:"),
        "PRODUCTION_DATABASE",
        "生产环境必须使用MySQL数据库");
    for (String name :
        List.of(
            "WX_APP_ID",
            "WX_APP_SECRET",
            "WX_MCH_ID",
            "WX_MERCHANT_SERIAL",
            "WX_PRIVATE_KEY_PATH",
            "WX_PLATFORM_SERIAL",
            "WX_PLATFORM_PUBLIC_KEY_PATH",
            "WX_API_V3_KEY",
            "SUPPORT_CONTACT",
            "PRIVACY_CONTACT")) settings.required(name);
    for (String name :
        List.of(
            "WX_LOGIN_URL",
            "WX_PAY_BASE_URL",
            "WX_PAY_NOTIFY_URL",
            "WX_REFUND_NOTIFY_URL",
            "PUBLIC_STORE_URL"))
      Problem.require(
          settings.required(name).startsWith("https://"), "INSECURE_ENDPOINT", name + " 必须使用HTTPS");
    Problem.require(
        settings.required("WX_API_V3_KEY").getBytes(StandardCharsets.UTF_8).length == 32,
        "KEY_CONFIGURATION",
        "API v3密钥必须为32字节");
    Problem.require(
        Files.isReadable(Path.of(settings.required("WX_PRIVATE_KEY_PATH")))
            && Files.isReadable(Path.of(settings.required("WX_PLATFORM_PUBLIC_KEY_PATH"))),
        "KEY_CONFIGURATION",
        "商户私钥或平台公钥文件不可读");
    if (settings.enabled("TRANSFER_ENABLED"))
      for (String name :
          List.of(
              "WX_TRANSFER_BASE_URL",
              "WX_TRANSFER_NOTIFY_URL",
              "WX_TRANSFER_SCENE_ID",
              "WX_TRANSFER_PERCEPTION",
              "WX_TRANSFER_REPORT_INFOS")) settings.required(name);
  }
}
