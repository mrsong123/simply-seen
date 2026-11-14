package com.sujian.mall.service;

import java.util.*;

public interface CommerceService {
  Map<String, Object> rules();

  List<Map<String, Object>> products(String category, String search);

  Map<String, Object> product(long id);

  List<Map<String, Object>> addresses(long uid);

  Object address(long uid, Map<String, Object> b);

  List<Map<String, Object>> cart(long uid);

  void cart(long uid, long sku, int qty);

  Map<String, Object> quote(long uid, Map<String, Object> b);

  Object idem(
      long uid,
      String operation,
      String key,
      Object body,
      java.util.function.Supplier<Object> action);

  Object create(long uid, String key, Map<String, Object> b);

  List<Map<String, Object>> orders(long uid);

  Map<String, Object> order(long uid, String no);

  void asset(long uid, String asset, long delta, String event, String order, String note);

  void paid(String no, String transaction, long amount);

  void benefits(String no);

  void closeLocal(String no);

  Object prepay(long uid, String no);

  void synchronizePayment(String no);

  void paymentEvidence(Map<String, Object> r);

  void cancel(long uid, String no);

  void ship(long admin, String no, Map<String, Object> body);

  void receive(long uid, String no);
}
