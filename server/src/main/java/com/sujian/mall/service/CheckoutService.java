package com.sujian.mall.service;

import java.util.*;

public interface CheckoutService {
  Map<String, Object> quote(long uid, Map<String, Object> b);
}
