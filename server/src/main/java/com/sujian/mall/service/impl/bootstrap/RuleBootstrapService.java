package com.sujian.mall.service.impl.bootstrap;

import com.sujian.mall.common.*;
import com.sujian.mall.config.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RuleBootstrapService {
  @org.springframework.beans.factory.annotation.Autowired
  private RuleVersionMapper ruleVersionMapper;

  private final BusinessSupport d;
  private final Settings c;

  public RuleBootstrapService(BusinessSupport d, Settings c) {
    this.d = d;
    this.c = c;
  }

  public void initialize() {
    if (ruleVersionMapper.countForRun() == 0) {
      var rule = new LinkedHashMap<String, Object>();
      rule.put("level1Bps", 500);
      rule.put("level2Bps", 200);
      rule.put("marginSharePercent", 30);
      rule.put("settlementDays", 7);
      rule.put("pointsPer100Yuan", 10);
      rule.put("freightFen", 600);
      rule.put("freeShippingFen", 19900);
      rule.put("monthlyBudgetFen", 100000);
      rule.put("monthlyCapFen", 50000);
      rule.put("monthlyPoolBps", 1000);
      rule.put("commissionBudgetFen", 1000000);
      rule.put("distributionEnabled", c.dev() || c.enabled("DISTRIBUTION_ENABLED"));
      ruleVersionMapper.insertForRun(d.id(), d.encode(rule), d.now());
    }
  }
}
