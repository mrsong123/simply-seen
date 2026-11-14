package com.sujian.mall.integration.identity;

import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.aliyun.AliyunRpcClient;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Id2MetaVerify 2019-03-07, grounded in com.aliyun:cloudauth20190307:3.14.2 sources. */
@Component
public class ConfiguredIdentityGateway implements IdentityGateway {
  private final Settings settings;
  private final AliyunRpcClient rpc;

  public ConfiguredIdentityGateway(Settings settings, AliyunRpcClient rpc) {
    this.settings = settings;
    this.rpc = rpc;
  }

  public Verification verify(String name, String idNumber) {
    String provider =
        settings
            .get("IDENTITY_PROVIDER", settings.dev() ? "development" : "aliyun")
            .trim()
            .toLowerCase(Locale.ROOT);
    if (provider.equals("development")) {
      if (!settings.dev())
        throw new Problem(503, "IDENTITY_PROVIDER_CONFIGURATION", "生产环境禁止使用开发实名渠道");
      return new Verification(true, "development-" + UUID.randomUUID(), "", true);
    }
    if (!provider.equals("aliyun"))
      throw new Problem(503, "IDENTITY_PROVIDER_CONFIGURATION", "实名认证服务商配置不受支持");
    var result =
        rpc.call(
            "IDENTITY",
            "Id2MetaVerify",
            "2019-03-07",
            Map.of("UserName", name, "IdentifyNum", idNumber, "ParamType", "normal"));
    String requestId = AliyunRpcClient.requestId(result);
    if (!"200".equals(String.valueOf(result.getOrDefault("Code", ""))))
      throw unavailable();
    if (!(result.get("ResultObject") instanceof Map<?, ?> outcome)) throw unavailable();
    String code = String.valueOf(outcome.get("BizCode"));
    return switch (code) {
      case "1" -> new Verification(true, requestId, "", false);
      case "2" -> new Verification(false, requestId, "姓名与身份证号码不一致，请核对后重试", false);
      case "3" -> new Verification(false, requestId, "认证渠道未查询到有效证件信息，请核对后重试", false);
      default -> throw unavailable();
    };
  }

  private Problem unavailable() {
    return new Problem(503, "IDENTITY_PROVIDER_UNAVAILABLE", "实名认证渠道暂不可用，请稍后重试或联系客服");
  }
}
