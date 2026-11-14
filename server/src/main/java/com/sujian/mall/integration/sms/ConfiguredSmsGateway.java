package com.sujian.mall.integration.sms;

import com.sujian.mall.common.JsonCodec;
import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.aliyun.AliyunRpcClient;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** SendSms 2017-05-25, grounded in com.aliyun:dysmsapi20170525:4.6.0 sources. */
@Component
public class ConfiguredSmsGateway implements SmsGateway {
  private final Settings settings;
  private final JsonCodec json;
  private final AliyunRpcClient rpc;

  public ConfiguredSmsGateway(Settings settings, JsonCodec json, AliyunRpcClient rpc) {
    this.settings = settings;
    this.json = json;
    this.rpc = rpc;
  }

  public Delivery send(String phone, String code) {
    String provider =
        settings
            .get("SMS_PROVIDER", settings.dev() ? "development" : "aliyun")
            .trim()
            .toLowerCase(Locale.ROOT);
    if (provider.equals("development")) {
      if (!settings.dev())
        throw new Problem(503, "SMS_PROVIDER_CONFIGURATION", "生产环境禁止使用开发短信渠道");
      return new Delivery("development-" + UUID.randomUUID(), true);
    }
    if (!provider.equals("aliyun"))
      throw new Problem(503, "SMS_PROVIDER_CONFIGURATION", "短信服务商配置不受支持，请联系运营人员");
    var result =
        rpc.call(
            "SMS",
            "SendSms",
            "2017-05-25",
            Map.of(
                "PhoneNumbers", phone,
                "SignName", settings.required("SMS_SIGN_NAME"),
                "TemplateCode", settings.required("SMS_TEMPLATE_CODE"),
                "TemplateParam", json.encode(Map.of("code", code))));
    String status = String.valueOf(result.getOrDefault("Code", ""));
    if (!status.equals("OK")) throw failure(status);
    return new Delivery(AliyunRpcClient.requestId(result), false);
  }

  private Problem failure(String code) {
    return switch (code) {
      case "isv.BUSINESS_LIMIT_CONTROL", "Throttling", "Throttling.User" ->
          new Problem(429, "SMS_PROVIDER_RATE_LIMIT", "短信发送过于频繁，请稍后再试");
      case "isv.MOBILE_NUMBER_ILLEGAL" ->
          new Problem(400, "PHONE_INVALID", "手机号码不可用，请检查后重试");
      case "isv.SMS_SIGNATURE_ILLEGAL", "isv.SMS_TEMPLATE_ILLEGAL", "SignatureDoesNotMatch",
          "InvalidAccessKeyId.NotFound" ->
          new Problem(503, "SMS_PROVIDER_CONFIGURATION", "短信服务配置需核对，请联系运营人员");
      default -> new Problem(503, "SMS_PROVIDER_REJECTED", "短信暂未发送成功，请稍后重试");
    };
  }
}
