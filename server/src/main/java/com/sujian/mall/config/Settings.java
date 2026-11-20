package com.sujian.mall.config;

import com.sujian.mall.common.Problem;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import com.sujian.mall.config.env.ConfigurationFiles;
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
    String value = raw(name);
    if (!Boolean.parseBoolean(raw("APP_CONFIG_FILES_RESOLVED")) && (value == null || value.isBlank())) {
      String file = raw(name + "_FILE");
      if (file != null && !file.isBlank()) value = ConfigurationFiles.secret(baseDirectory(), file, name);
    }
    if (value == null || value.isBlank()) {
      String derived = derived(name);
      if (derived != null) value = derived;
    }
    if (value == null) value = fallback;
    if (name.endsWith("_BASE_URL") || name.equals("PUBLIC_STORE_URL") || name.equals("PUBLIC_API_URL") || name.equals("PUBLIC_ASSET_URL"))
      value = value.replaceAll("/+$", "");
    if (name.endsWith("_PATH") && !value.isBlank())
      return ConfigurationFiles.resolve(baseDirectory(), value).toString();
    return value;
  }

  private String raw(String name) {
    return environment == null ? System.getenv(name) : environment.getProperty(name);
  }

  private Path baseDirectory() {
    String configured = raw("APP_CONFIG_BASE_DIR");
    return Path.of(configured == null || configured.isBlank() ? System.getProperty("user.dir") : configured)
        .toAbsolutePath().normalize();
  }

  private String derived(String name) {
    boolean embedded = !"external".equals(raw("DATABASE_MODE"));
    if (embedded && name.equals("DATABASE_USER")) return raw("MYSQL_USER");
    if (embedded && name.equals("DATABASE_PASSWORD")) return raw("MYSQL_PASSWORD");
    if (name.equals("DATABASE_URL")) {
      String database = raw("MYSQL_DATABASE");
      if (embedded && database != null && database.matches("[A-Za-z0-9_]{1,64}"))
        return "jdbc:mysql://mysql:3306/" + database + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&sslMode=REQUIRED";
    }
    String store = raw("PUBLIC_STORE_URL");
    if (store == null || store.isBlank()) return null;
    store = store.replaceAll("/+$", "");
    if (name.equals("PUBLIC_API_URL")) return store + "/api/v1";
    if (name.equals("PUBLIC_ASSET_URL")) return store;
    String api = raw("PUBLIC_API_URL");
    if (api == null || api.isBlank()) api = store + "/api/v1";
    api = api.replaceAll("/+$", "");
    return switch (name) {
      case "WX_PAY_NOTIFY_URL" -> api + "/callbacks/wechat/pay";
      case "WX_REFUND_NOTIFY_URL" -> api + "/callbacks/wechat/refund";
      case "WX_TRANSFER_NOTIFY_URL" -> api + "/callbacks/wechat/transfer";
      default -> null;
    };
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
    result.put("webPaymentMode", get("WX_WEB_PAYMENT_MODE", "native").trim().toLowerCase(java.util.Locale.ROOT));
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
