package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenefitsServiceImpl implements BenefitsService {
  @org.springframework.beans.factory.annotation.Autowired private CommissionMapper commissionMapper;
  @org.springframework.beans.factory.annotation.Autowired private OrderItemMapper orderItemMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SystemGuardMapper systemGuardMapper;

  private final BusinessSupport d;
  private final AssetService assets;

  public BenefitsServiceImpl(BusinessSupport d, AssetService assets) {
    this.d = d;
    this.assets = assets;
  }

  @Transactional
  public void benefits(String no) {
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.lockOrderByNo(no));
    if (!s(o, "payment_state").equals("SUCCESS")) return;
    long uid = n(o, "member_id");
    var rule = d.decode(s(o, "rule_snapshot"));
    long points =
        Math.max(0, n(o, "cash_due") - n(o, "freight") - n(o, "cash_refunded"))
            / 10000
            * n(rule, "pointsPer100Yuan");
    if (points > 0) assets.asset(uid, "points", points, no + ":earned", no, "有效现金消费赠积分");
    commission(o, rule);
  }

  @SuppressWarnings("unchecked")
  private void commission(Map<String, Object> o, Map<String, Object> rule) {
    if (!b(rule, "distributionEnabled")) return;
    String no = s(o, "id");
    if (commissionMapper.countForCommission(no) > 0) return;
    long pool = 0;
    for (var l : orderItemMapper.listForCommission(no)) {
      long cash = n(l, "cash_amount");
      long margin = Math.max(0, cash - n(l, "cost") * n(l, "quantity"));
      pool +=
          Math.min(
              cash * n(l, "commission_percent") / 100,
              margin * n(rule, "marginSharePercent") / 100);
    }
    com.sujian.mall.common.Rows.required(systemGuardMapper.findForCommission());
    ZoneId budgetZone = ZoneId.of("Asia/Shanghai");
    YearMonth paidMonth = YearMonth.from(Instant.ofEpochMilli(n(o, "paid_at")).atZone(budgetZone));
    long paidMonthStart = paidMonth.atDay(1).atStartOfDay(budgetZone).toInstant().toEpochMilli();
    long nextPaidMonthStart =
        paidMonth.plusMonths(1).atDay(1).atStartOfDay(budgetZone).toInstant().toEpochMilli();
    // A locking read sees the latest committed allocations even when an enclosing
    // MySQL REPEATABLE_READ transaction established its snapshot before the guard.
    long used =
        commissionMapper.listOriginalAmountsForPaidMonthBudget(paidMonthStart, nextPaidMonthStart)
            .stream().mapToLong(Long::longValue).sum();
    pool = Math.min(pool, Math.max(0, n(rule, "commissionBudgetFen") - used));
    long base = Math.max(0, n(o, "cash_due") - n(o, "freight"));
    long first = base * n(rule, "level1Bps") / 10000,
        second = base * n(rule, "level2Bps") / 10000,
        sum = first + second;
    if (sum > pool && sum > 0) {
      first = pool * first / sum;
      second = pool - first;
    }
    for (var person : (List<Map<String, Object>>) rule.get("beneficiaries")) {
      long level = n(person, "level"), amount = level == 1 ? first : second;
      if (amount > 0)
        commissionMapper.insertForCommission(
            d.id(),
            no,
            n(person, "id"),
            n(o, "member_id"),
            level,
            amount,
            amount,
            d.encode(rule),
            d.now());
    }
  }
}
