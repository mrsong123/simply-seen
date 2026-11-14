package com.sujian.mall.service;

import java.util.*;

public interface FulfillmentService {
  void ship(long admin, String no, Map<String, Object> body);

  void receive(long uid, String no);
}
