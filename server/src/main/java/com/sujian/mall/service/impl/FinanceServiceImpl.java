package com.sujian.mall.service.impl;

import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FinanceServiceImpl implements FinanceService {
  private final RefundApplicationService refundApplicationService;
  private final RefundSettlementService refundSettlementService;
  private final CommissionSettlementService commissionSettlementService;
  private final WithdrawalService withdrawalService;
  private final MonthlySettlementService monthlySettlementService;
  private final MarketingRuleService marketingRuleService;

  public FinanceServiceImpl(
      RefundApplicationService refundApplicationService,
      RefundSettlementService refundSettlementService,
      CommissionSettlementService commissionSettlementService,
      WithdrawalService withdrawalService,
      MonthlySettlementService monthlySettlementService,
      MarketingRuleService marketingRuleService) {
    this.refundApplicationService = refundApplicationService;
    this.refundSettlementService = refundSettlementService;
    this.commissionSettlementService = commissionSettlementService;
    this.withdrawalService = withdrawalService;
    this.monthlySettlementService = monthlySettlementService;
    this.marketingRuleService = marketingRuleService;
  }

  public Object refund(long uid, String key, Map<String, Object> b) {
    return refundApplicationService.refund(uid, key, b);
  }

  public void reviewRefund(long actor, String id, boolean approve, String reason) {
    refundApplicationService.reviewRefund(actor, id, approve, reason);
  }

  public void refundSuccess(String id, String channelId) {
    refundSettlementService.refundSuccess(id, channelId);
  }

  public void settleCommissions() {
    commissionSettlementService.settleCommissions();
  }

  public Object withdraw(long uid, String key, Map<String, Object> b) {
    return withdrawalService.withdraw(uid, key, b);
  }

  public void reviewWithdrawal(long actor, String id, Map<String, Object> body) {
    withdrawalService.reviewWithdrawal(actor, id, body);
  }

  public void executeWithdrawal(long actor, String id) {
    withdrawalService.executeWithdrawal(actor, id);
  }

  public void transferResult(String id, Map<String, Object> result) {
    withdrawalService.transferResult(id, result);
  }

  public Object monthly(long actor, String month) {
    return monthlySettlementService.monthly(actor, month);
  }

  public void approveMonthly(long actor, long id) {
    monthlySettlementService.approveMonthly(actor, id);
  }

  public void saveRule(long actor, Map<String, Object> b) {
    marketingRuleService.saveRule(actor, b);
  }

  public void approveRule(long actor, long id) {
    marketingRuleService.approveRule(actor, id);
  }
}
