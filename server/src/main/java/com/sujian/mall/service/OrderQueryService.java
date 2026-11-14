package com.sujian.mall.service;

import java.util.*;

public interface OrderQueryService {
  List<Map<String, Object>> orders(long uid);

  Map<String, Object> order(long uid, String no);
}
