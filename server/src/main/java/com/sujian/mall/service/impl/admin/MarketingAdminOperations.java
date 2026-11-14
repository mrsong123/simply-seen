package com.sujian.mall.service.impl.admin;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.BusinessSupport;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.FinanceService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MarketingAdminOperations {
  @org.springframework.beans.factory.annotation.Autowired
  private RuleVersionMapper ruleVersionMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SettlementAwardMapper settlementAwardMapper;

  @org.springframework.beans.factory.annotation.Autowired private SettlementMapper settlementMapper;

  private final BusinessSupport d;
  private final FinanceService f;

  public MarketingAdminOperations(BusinessSupport d, FinanceService f) {
    this.d = d;
    this.f = f;
  }

  public Object adminRules() {
    return ruleVersionMapper.listForAdminRules();
  }

  public void saveRule(long actor, Map<String, Object> b) {
    f.saveRule(actor, b);
  }

  public void approveRule(long actor, long id) {
    f.approveRule(actor, id);
  }

  public Object settlements() {
    var rows = settlementMapper.listForSettlements();
    for (var s : rows) s.put("awards", settlementAwardMapper.listForSettlements(n(s, "id")));
    return rows;
  }

  public Object settlement(long actor, Map<String, Object> b) {
    return f.monthly(actor, s(b, "period"));
  }

  public void approveSettlement(long actor, long id) {
    f.approveMonthly(actor, id);
  }
}
