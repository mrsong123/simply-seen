package com.sujian.mall.service;

public interface PaymentSettlementService {
  void paid(String orderNo, String transactionId, long amount);
  void closeLocal(String orderNo);
}
