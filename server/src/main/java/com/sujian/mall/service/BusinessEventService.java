package com.sujian.mall.service;

public interface BusinessEventService {
  void audit(long actor, String action, String resource, Object detail);

  void issue(String kind, String resource, String detail);

  void event(String key, String kind, String aggregate);
}
