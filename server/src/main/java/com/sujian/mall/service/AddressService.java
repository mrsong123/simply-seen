package com.sujian.mall.service;

import java.util.*;

public interface AddressService {
  List<Map<String, Object>> addresses(long uid);

  Object address(long uid, Map<String, Object> b);
}
