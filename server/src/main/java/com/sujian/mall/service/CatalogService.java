package com.sujian.mall.service;

import java.util.*;

public interface CatalogService {
  Map<String, Object> rules();

  List<Map<String, Object>> products(String category, String search);

  Map<String, Object> product(long id);
}
