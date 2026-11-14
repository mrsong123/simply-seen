package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommissionSettlementServiceImpl implements CommissionSettlementService {
  @org.springframework.beans.factory.annotation.Autowired private CommissionMapper commissionMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;

  private final BusinessSupport d;
  private final CommerceService shop;

  public CommissionSettlementServiceImpl(BusinessSupport d, CommerceService shop) {
    this.d = d;
    this.shop = shop;
  }

  @Transactional
  public void settleCommissions() {
    long now = d.now();
    for (var row : commissionMapper.listForSettleCommissions()) {
      var comm =
          com.sujian.mall.common.Rows.required(
              commissionMapper.findForSettleCommissions(n(row, "id")));
      var o =
          com.sujian.mall.common.Rows.required(
              shopOrderMapper.selectOrderByNo(s(comm, "order_id")));
      var rule = d.decode(s(comm, "rule_snapshot"));
      if (!s(comm, "state").equals("PENDING")
          || n(o, "completed_at") + n(rule, "settlementDays") * 86400000L > now) continue;
      if (refundMapper.countForShip(s(comm, "order_id")) > 0) continue;
      long uid = n(comm, "beneficiary_id");
      var m = com.sujian.mall.common.Rows.required(memberMapper.lockAccountById(uid));
      if (b(m, "blocked")) continue;
      long debt = Math.min(n(m, "debt"), n(comm, "amount"));
      if (debt > 0)
        shop.asset(
            uid,
            "debt",
            -debt,
            "C" + n(comm, "id") + ":offset",
            s(comm, "order_id"),
            "新佣金抵偿历史退款负债");
      if (n(comm, "amount") > debt)
        shop.asset(
            uid,
            "available",
            n(comm, "amount") - debt,
            "C" + n(comm, "id") + ":settle",
            s(comm, "order_id"),
            "售后期结束，成交佣金结算");
      commissionMapper.updateForSettleCommissions(now, n(comm, "id"));
    }
  }
}
