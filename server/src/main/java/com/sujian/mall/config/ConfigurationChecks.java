package com.sujian.mall.config;

import java.net.URI;
import java.util.List;

final class ConfigurationChecks {
  private ConfigurationChecks() {}
  static String required(Settings settings, String name, List<String> errors) {
    try {
      String value = settings.get(name, "");
      if (value.isBlank()) errors.add("缺少 " + name + "（或" + name + "_FILE）");
      return value;
    } catch (RuntimeException failure) {
      errors.add("无法读取 " + name + " 的配置文件");
      return "";
    }
  }
  static void https(Settings settings, String name, List<String> errors) {
    String value = required(settings, name, errors);
    if (value.isBlank()) return;
    try {
      URI uri = URI.create(value);
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
        errors.add(name + "必须为有效HTTPS地址且不能含凭据或片段");
    } catch (IllegalArgumentException error) { errors.add(name + "地址格式无效"); }
  }
  static void integer(Settings settings, String name, int fallback, int minimum, int maximum, List<String> errors) {
    try {
      int value = settings.integer(name, fallback);
      if (value < minimum || value > maximum) errors.add(name + "超出允许范围" + minimum + ".." + maximum);
    } catch (RuntimeException error) { errors.add(name + "必须为整数"); }
  }
}
