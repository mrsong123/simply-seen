package com.sujian.mall.common;

public final class PhoneNumbers {
  private PhoneNumbers() {}

  public static String normalize(String value) {
    String phone = value == null ? "" : value.trim();
    if (!phone.matches("1[3-9][0-9]{9}"))
      throw new Problem(400, "INVALID_PHONE", "请输入有效的中国大陆手机号码");
    return phone;
  }

  public static String masked(String phone) {
    return phone.substring(0, 3) + "****" + phone.substring(7);
  }
}
