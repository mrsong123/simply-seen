package com.sujian.mall.common;


public final class Rows {
  private Rows() {}

  public static <T> T required(T value) {
    if (value == null) throw new Problem(404, "NOT_FOUND", "记录不存在或无权访问");
    return value;
  }
}
