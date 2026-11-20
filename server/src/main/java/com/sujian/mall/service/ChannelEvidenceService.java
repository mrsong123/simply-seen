package com.sujian.mall.service;

import java.util.Map;

/** Normalizes only verified provider messages and checks them against durable merchant records. */
public interface ChannelEvidenceService {
  Map<String, Object> refund(String refundNo, Map<String, Object> evidence);

  void transfer(String withdrawalNo, Map<String, Object> evidence, boolean creationReceipt);
}
