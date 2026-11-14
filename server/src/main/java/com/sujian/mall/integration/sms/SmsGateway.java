package com.sujian.mall.integration.sms;

/** SMS delivery acceptance; the business service owns OTP lifetime and consumption. */
public interface SmsGateway {
  record Delivery(String requestId, boolean development) {}

  Delivery send(String phone, String code);
}
