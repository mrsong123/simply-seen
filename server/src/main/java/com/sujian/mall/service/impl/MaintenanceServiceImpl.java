package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceServiceImpl implements MaintenanceService {
  @org.springframework.beans.factory.annotation.Autowired private QuoteMapper quoteMapper;
  @org.springframework.beans.factory.annotation.Autowired private RateLimitMapper rateLimitMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SessionTokenMapper sessionTokenMapper;

  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;

  private final BusinessSupport d;
  private final CommerceService shop;
  private final FinanceService finance;
  private final org.springframework.transaction.support.TransactionTemplate tx;

  public MaintenanceServiceImpl(
      BusinessSupport d,
      CommerceService shop,
      FinanceService finance,
      org.springframework.transaction.support.TransactionTemplate tx) {
    this.d = d;
    this.shop = shop;
    this.finance = finance;
    this.tx = tx;
  }

  public void maintain() {
    for (var o : shopOrderMapper.selectExpiredUnpaidOrders(d.now()))
      try {
        shop.cancel(n(o, "member_id"), s(o, "id"));
      } catch (Exception e) {
        tx.executeWithoutResult(t -> d.issue("PAYMENT_UNKNOWN", s(o, "id"), "超时订单需原单查单确认，预占仍保留"));
      }
    try {
      finance.settleCommissions();
    } catch (Exception e) {
      tx.executeWithoutResult(t -> d.issue("COMMISSION_SETTLE", "JOB", "佣金结算未完成，请检查数据库和账本"));
    }
    sessionTokenMapper.deleteForMaintain(d.now());
    quoteMapper.deleteForMaintain(d.now() - 86400000);
    rateLimitMapper.deleteForMaintain(d.now() - 86400000);
  }
}
