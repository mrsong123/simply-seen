package com.sujian.mall.service;

import java.util.*;

public interface PaymentService {
  void paid(String no, String transaction, long amount);

  void closeLocal(String no);

  Object prepay(long uid, String no);

  Object prepay(long uid, String no, String channel, String clientIp);

  void synchronizePayment(String no);

  void paymentEvidence(Map<String, Object> r);

  void cancel(long uid, String no);
}
