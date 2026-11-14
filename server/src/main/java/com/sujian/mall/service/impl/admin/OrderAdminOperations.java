package com.sujian.mall.service.impl.admin;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.BusinessSupport;
import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.job.OutboxJob;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.CommerceService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderAdminOperations {
  @org.springframework.beans.factory.annotation.Autowired private IssueMapper issueMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;
  @org.springframework.beans.factory.annotation.Autowired private WithdrawalMapper withdrawalMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final CommerceService shop;
  private final OutboxJob jobs;

  public OrderAdminOperations(BusinessSupport d, Settings c, CommerceService shop, OutboxJob jobs) {
    this.d = d;
    this.c = c;
    this.shop = shop;
    this.jobs = jobs;
  }

  public Object payStatus(long uid, String no) {
    var o = shop.order(uid, no);
    if (!c.dev() && s(o, "state").equals("CREATED")) shop.synchronizePayment(no);
    return shop.order(uid, no);
  }

  public void devPay(long uid, String no) {
    Problem.require(c.dev(), "DEV_DISABLED", "仅开发环境允许测试付款");
    var o = shop.order(uid, no);
    shop.paid(no, "DEV-" + no, n(o, "cash_due"));
    jobs.run();
  }

  public Object summary() {
    return Map.of(
        "members",
        memberMapper.countMembers(),
        "orders",
        shopOrderMapper.countOrders(),
        "gmv",
        shopOrderMapper.sumNetPaidCash(),
        "pendingOrders",
        shopOrderMapper.countAwaitingShipment(),
        "pendingRefunds",
        refundMapper.countForSummary(),
        "pendingWithdrawals",
        withdrawalMapper.countForSummary(),
        "issues",
        issueMapper.countForSummary(),
        "trend",
        shopOrderMapper.selectRecentOrderTrend());
  }

  public Object adminOrders() {
    return shopOrderMapper.selectAdminOrders();
  }

  public Object adminOrder(String no) {
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.selectOrderOwner(no));
    return shop.order(n(o, "member_id"), no);
  }

  public Object refunds() {
    return refundMapper.listForRefunds();
  }
}
