package com.sujian.mall.service;

public interface PaymentCancellationService {
  void cancel(long memberId, String orderNo);
}
