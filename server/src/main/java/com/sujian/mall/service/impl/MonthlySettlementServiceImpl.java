package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlySettlementServiceImpl implements MonthlySettlementService {
  @org.springframework.beans.factory.annotation.Autowired
  private RuleVersionMapper ruleVersionMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SettlementAwardMapper settlementAwardMapper;

  @org.springframework.beans.factory.annotation.Autowired private SettlementMapper settlementMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;

  @org.springframework.beans.factory.annotation.Autowired private SystemGuardMapper guards;

  private final BusinessSupport d;
  private final Settings c;
  private final CommerceService shop;

  public MonthlySettlementServiceImpl(BusinessSupport d, Settings c, CommerceService shop) {
    this.d = d;
    this.c = c;
    this.shop = shop;
  }

  @Transactional
  public Object monthly(long actor, String month) {
    Problem.require(c.enabled("MONTHLY_ENABLED") || c.dev(), "MARKETING_DISABLED", "月度活动未开放");
    YearMonth ym;
    try {
      ym = YearMonth.parse(month);
    } catch (Exception e) {
      throw new Problem(400, "INVALID_MONTH", "月份格式为YYYY-MM");
    }
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    Problem.require(ym.isBefore(YearMonth.now(zone)), "PERIOD_OPEN", "仅能结算已结束月份");
    com.sujian.mall.common.Rows.required(guards.findForApproveRule());
    var rule = shop.rules();
    com.sujian.mall.common.Rows.required(ruleVersionMapper.findForMonthly(n(rule, "version")));
    var existing = settlementMapper.listForMonthly(month);
    long id;
    if (!existing.isEmpty()) {
      var e = existing.getFirst();
      Problem.require(s(e, "state").equals("INVALIDATED"), "SETTLEMENT_EXISTS", "本月结算已存在");
      id = n(e, "id");
      settlementAwardMapper.deleteForMonthly(id);
      settlementMapper.updateForMonthly(actor, id);
    } else {
      id = d.id();
      settlementMapper.insertForMonthly(id, month, actor, d.now());
    }
    long from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
    var rows = shopOrderMapper.selectMonthlyNetSales(from, to);
    long total = rows.stream().mapToLong(x -> n(x, "sales")).sum();
    long budget = Math.min(total * n(rule, "monthlyPoolBps") / 10000, n(rule, "monthlyBudgetFen")),
        left = budget;
    int limit = (rows.size() + 9) / 10;
    for (int i = 0; i < limit && left > 0; i++) {
      var row = rows.get(i);
      long award = Math.min(Math.min(n(row, "sales"), n(rule, "monthlyCapFen")), left);
      settlementAwardMapper.insertForMonthly(
          d.id(), id, n(row, "member_id"), n(row, "sales"), award, i + 1);
      left -= award;
    }
    settlementMapper.updateForMonthly2(budget, id);
    d.audit(actor, "MONTHLY_DRAFT", month, Map.of("budget", budget, "used", budget - left));
    return Map.of("id", id, "budget", budget);
  }

  @Transactional
  public void approveMonthly(long actor, long id) {
    com.sujian.mall.common.Rows.required(guards.findForApproveRule());
    var st = com.sujian.mall.common.Rows.required(settlementMapper.findForApproveMonthly(id));
    Problem.require(
        s(st, "state").equals("DRAFT") && n(st, "creator") != actor,
        "DUAL_REVIEW_REQUIRED",
        "结算需由另一名财务人员复核");
    Problem.require(c.enabled("MONTHLY_ENABLED") || c.dev(), "MARKETING_DISABLED", "月度活动未开放");
    for (var a : settlementAwardMapper.listForApproveMonthly(id))
      shop.asset(
          n(a, "member_id"),
          "available",
          n(a, "amount"),
          "MONTHLY:" + n(a, "id"),
          null,
          "月度优惠权益经双人复核入账");
    settlementMapper.updateForApproveMonthly(actor, id);
    d.audit(actor, "MONTHLY_APPROVE", "" + id, Map.of());
  }
}
