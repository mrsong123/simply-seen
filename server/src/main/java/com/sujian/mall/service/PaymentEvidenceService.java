package com.sujian.mall.service;

import java.util.Map;

public interface PaymentEvidenceService {
  void synchronize(String orderNo);
  void apply(Map<String, Object> evidence);
}
