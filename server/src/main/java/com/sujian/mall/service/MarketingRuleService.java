package com.sujian.mall.service;

import java.util.*;

public interface MarketingRuleService {
  void saveRule(long actor, Map<String, Object> b);

  void approveRule(long actor, long id);
}
