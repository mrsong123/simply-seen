package com.sujian.mall.service;

import java.util.*;

public interface CartService {
  List<Map<String, Object>> cart(long uid);

  void cart(long uid, long sku, int qty);
}
