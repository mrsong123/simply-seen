package com.sujian.mall.config;

import com.sujian.mall.common.Problem;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Environment variables override local YAML; production never enables simulation. */
@Component
public class Settings {
  private final Environment environment;

  public Settings() {
    this.environment = null;
  }

  @Autowired
  public Settings(Environment environment) {
    this.environment = environment;
  }

  public String get(String name, String fallback) {
    return environment == null
        ? System.getenv().getOrDefault(name, fallback)
        : environment.getProperty(name, fallback);
  }

  public String required(String name) {
    String value = get(name, "");
    if (value.isBlank()) throw new Problem(503, "CONFIG_REQUIRED", "尚未配置 " + name);
    return value;
  }

  public boolean dev() {
    return (environment == null || !environment.matchesProfiles("prod"))
        && Boolean.parseBoolean(get("APP_DEV_MODE", "false"));
  }

  public int integer(String name, int fallback) {
    return Integer.parseInt(get(name, String.valueOf(fallback)));
  }

  public boolean enabled(String name) {
    return Boolean.parseBoolean(get(name, "false"));
  }

  public boolean identityRequiredForWithdrawal() {
    return !dev() || Boolean.parseBoolean(get("IDENTITY_REQUIRED_FOR_WITHDRAWAL", "true"));
  }

  public Map<String, Object> publicConfig() {
    var result = new LinkedHashMap<String, Object>();
    result.put("devMode", dev());
    result.put("phoneLoginEnabled", true);
    result.put("identityVerificationEnabled", true);
    result.put("identityRequiredForWithdrawal", identityRequiredForWithdrawal());
    result.put("distributionEnabled", enabled("DISTRIBUTION_ENABLED"));
    result.put("transferEnabled", enabled("TRANSFER_ENABLED"));
    result.put("monthlyEnabled", enabled("MONTHLY_ENABLED"));
    result.put("brand", "素见好物");
    result.put("appId", get("WX_APP_ID", ""));
    result.put("mchId", get("WX_MCH_ID", ""));
    result.put("support", get("SUPPORT_CONTACT", "在线客服"));
    result.put("shareBase", get("PUBLIC_STORE_URL", ""));
    result.put("privacy", get("PRIVACY_CONTACT", "请通过客服申请个人信息查询与删除"));
    result.put("logisticsEnabled", !get("LOGISTICS_QUERY_URL", "").isBlank());
    return result;
  }
}
