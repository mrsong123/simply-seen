package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketingRuleServiceImpl implements MarketingRuleService {
  @org.springframework.beans.factory.annotation.Autowired
  private RuleVersionMapper ruleVersionMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SystemGuardMapper systemGuardMapper;

  private final BusinessSupport d;
  private final Settings c;

  public MarketingRuleServiceImpl(BusinessSupport d, Settings c) {
    this.d = d;
    this.c = c;
  }

  @Transactional
  public void saveRule(long actor, Map<String, Object> b) {
    long l1 = n(b, "level1Bps"), l2 = n(b, "level2Bps");
    Problem.require(
        l1 >= 0
            && l2 >= 0
            && l1 + l2 <= 1000
            && n(b, "marginSharePercent") >= 0
            && n(b, "marginSharePercent") <= 50
            && n(b, "settlementDays") >= 7
            && n(b, "settlementDays") <= 90
            && n(b, "pointsPer100Yuan") >= 0
            && n(b, "pointsPer100Yuan") <= 100
            && n(b, "freightFen") >= 0
            && n(b, "freeShippingFen") >= 0
            && n(b, "monthlyBudgetFen") >= 0
            && n(b, "monthlyCapFen") <= 50000
            && n(b, "monthlyCapFen") >= 0
            && n(b, "monthlyPoolBps") >= 0
            && n(b, "monthlyPoolBps") <= 1000
            && n(b, "commissionBudgetFen") >= 0,
        "INVALID_RULE",
        "规则超出预算或比例护栏：佣金合计≤10%，毛利分成≤50%，结算7至90天，月奖励每人≤500元");
    var clean = new LinkedHashMap<String, Object>();
    for (String k :
        List.of(
            "level1Bps",
            "level2Bps",
            "marginSharePercent",
            "settlementDays",
            "pointsPer100Yuan",
            "freightFen",
            "freeShippingFen",
            "monthlyBudgetFen",
            "monthlyCapFen",
            "monthlyPoolBps",
            "commissionBudgetFen")) clean.put(k, n(b, k));
    clean.put("distributionEnabled", b(b, "distributionEnabled"));
    ruleVersionMapper.insertForSaveRule(d.id(), d.encode(clean), actor, d.now());
    d.audit(actor, "RULE_DRAFT", "RULE", clean);
  }

  @Transactional
  public void approveRule(long actor, long id) {
    com.sujian.mall.common.Rows.required(systemGuardMapper.findForApproveRule());
    var rule = com.sujian.mall.common.Rows.required(ruleVersionMapper.findForApproveRule(id));
    Problem.require(
        s(rule, "state").equals("DRAFT") && n(rule, "creator") != actor,
        "DUAL_REVIEW_REQUIRED",
        "规则需由另一名管理员复核");
    var config = d.decode(s(rule, "config"));
    Problem.require(
        !b(config, "distributionEnabled") || c.enabled("DISTRIBUTION_ENABLED") || c.dev(),
        "MARKETING_DISABLED",
        "环境未开启分销，请先完成业务审批");
    ruleVersionMapper.updateForApproveRule();
    ruleVersionMapper.updateForApproveRule2(actor, id);
    d.audit(actor, "RULE_APPROVE", "" + id, config);
  }
}
