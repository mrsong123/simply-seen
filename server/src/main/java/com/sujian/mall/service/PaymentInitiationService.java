package com.sujian.mall.service;

public interface PaymentInitiationService {
  Object prepay(long memberId, String orderNo, String channel, String clientIp);
}
