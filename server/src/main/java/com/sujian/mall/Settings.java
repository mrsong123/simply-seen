package com.sujian.mall;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class Settings {
  public String get(String name, String fallback) {
    return System.getenv().getOrDefault(name, fallback);
  }

  public String required(String name) {
    String v = get(name, "");
    if (v.isBlank()) throw new Problem(503, "CONFIG_REQUIRED", "尚未配置 " + name);
    return v;
  }

  public boolean dev() {
    return Boolean.parseBoolean(get("APP_DEV_MODE", "false"));
  }

  public int integer(String name, int fallback) {
    return Integer.parseInt(get(name, "" + fallback));
  }

  public boolean enabled(String name) {
    return Boolean.parseBoolean(get(name, "false"));
  }

  public Map<String, Object> publicConfig() {
    var result = new LinkedHashMap<String, Object>();
    result.put("devMode", dev());
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
