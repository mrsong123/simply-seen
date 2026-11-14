package com.sujian.mall.service;

import java.util.*;

public interface OutboxProcessingService {
  void run();

  void refund(String no, long attempt);

  void refundResult(String no, Map<String, Object> result);

  void transfer(String no, long attempt);

  void validateTransfer(String no, Map<String, Object> result);
}
