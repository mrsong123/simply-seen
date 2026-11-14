package com.sujian.mall.service;

import java.util.*;

public interface RefundApplicationService {
  Object refund(long uid, String key, Map<String, Object> b);

  void reviewRefund(long actor, String id, boolean approve, String reason);
}
