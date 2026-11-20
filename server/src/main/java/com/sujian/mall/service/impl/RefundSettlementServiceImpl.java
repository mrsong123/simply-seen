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
public class RefundSettlementServiceImpl implements RefundSettlementService {
  @org.springframework.beans.factory.annotation.Autowired private CommissionMapper commissionMapper;
  @org.springframework.beans.factory.annotation.Autowired private LedgerMapper ledgerMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private OrderItemMapper orderItemMapper;
  @org.springframework.beans.factory.annotation.Autowired private RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired private SettlementMapper settlementMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;
  @org.springframework.beans.factory.annotation.Autowired private SkuMapper skuMapper;

  @org.springframework.beans.factory.annotation.Autowired private SystemGuardMapper guards;

  private final BusinessSupport d;
  private final CommerceService shop;

  public RefundSettlementServiceImpl(BusinessSupport d, CommerceService shop) {
    this.d = d;
    this.shop = shop;
  }

  @Transactional
  public void refundSuccess(String id, String channelId) {
    var r = com.sujian.mall.common.Rows.required(refundMapper.findForReviewRefund(id));
    Problem.require(channelId != null && !channelId.isBlank(), "REFUND_EVIDENCE", "缺少可信退款交易单号");
    if (s(r, "state").equals("SUCCESS")) {
      Problem.require(s(r, "channel_id").equals(channelId), "REFUND_CONFLICT", "重复退款通知的渠道交易单号不一致");
      return;
    }
    Problem.require(
        List.of("REVIEWED", "PROCESSING", "ABNORMAL").contains(s(r, "state")),
        "REFUND_STATE",
        "退款单尚未审核");
    String no = s(r, "order_id");
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.lockOrderByNo(no));
    com.sujian.mall.common.Rows.required(guards.findForApproveRule());
    String month = Instant.ofEpochMilli(n(o, "paid_at")).atZone(ZoneId.of("Asia/Shanghai")).toString().substring(0, 7);
    var monthlySettlements = settlementMapper.listForRefundSuccess(month);
    shop.benefits(no);
    long uid = n(r, "member_id");
    com.sujian.mall.common.Rows.required(memberMapper.lockMemberId(uid));
    for (var line : d.list(s(r, "line_snapshot"))) {
      orderItemMapper.updateForRefundSuccess(
          n(line, "quantity"), n(line, "cash"), n(line, "points"), n(line, "id"));
      if (s(o, "state").equals("PAID"))
        skuMapper.restoreRefundedStock(n(line, "quantity"), n(line, "id"));
    }
    long refundCash = n(o, "cash_refunded") + n(r, "amount"),
        refundPoints = n(o, "points_refunded") + n(r, "points");
    Problem.require(
        refundCash <= n(o, "cash_due") && refundPoints <= n(o, "points_used"),
        "REFUND_AMOUNT_EXCEEDED",
        "累计退款超限");
    if (n(r, "points") > 0)
      shop.asset(uid, "points", n(r, "points"), id + ":points-back", no, "售后退回抵扣积分");
    var rule = d.decode(s(o, "rule_snapshot"));
    long original = Math.max(0, n(o, "cash_due") - n(o, "freight"));
    long cashNet = Math.max(0, original - refundCash);
    long earned = original / 10000 * n(rule, "pointsPer100Yuan"),
        remaining = cashNet / 10000 * n(rule, "pointsPer100Yuan");
    long already = -ledgerMapper.countForRefundSuccess(uid, no);
    long revoke = Math.max(0, earned - remaining - already);
    if (revoke > 0) {
      var m = com.sujian.mall.common.Rows.required(memberMapper.lockAccountById(uid));
      long take = Math.min(n(m, "points"), revoke);
      if (take > 0) shop.asset(uid, "points", -take, id + ":earned-revoke", no, "退款回收赠送积分");
      if (take < revoke)
        d.issue("POINTS_RECOVERY", id, "赠送积分不足回收，缺口 " + (revoke - take) + " 点；暂停该会员新佣金提现，待人工核销");
    }
    for (var comm : commissionMapper.listForRefundSuccess(no)) {
      long target = original == 0 ? 0 : n(comm, "original_amount") * cashNet / original,
          delta = n(comm, "amount") - target;
      long beneficiary = n(comm, "beneficiary_id");
      if (delta > 0 && s(comm, "state").equals("SETTLED")) {
        var m = com.sujian.mall.common.Rows.required(memberMapper.lockAccountById(beneficiary));
        long take = Math.min(delta, n(m, "available"));
        if (take > 0)
          shop.asset(
              beneficiary,
              "available",
              -take,
              id + ":commission-revoke:" + beneficiary,
              no,
              "来源订单退款冲回佣金");
        if (take < delta) {
          shop.asset(
              beneficiary,
              "debt",
              delta - take,
              id + ":commission-debt:" + beneficiary,
              no,
              "已结佣金待追偿");
          d.issue(
              "COMMISSION_RECOVERY",
              id + ":" + beneficiary,
              "佣金已冻结或出款，待追偿 " + (delta - take) + " 分");
        }
      }
      commissionMapper.updateForRefundSuccess(
          target, target == 0 ? "REVERSED" : s(comm, "state"), n(comm, "id"));
    }
    boolean full = orderItemMapper.countForRefund(no) == 0;
    shopOrderMapper.recordRefundTotals(
        refundCash, refundPoints, full ? "REFUNDED" : s(o, "state"), no);
    refundMapper.updateForRefundSuccess(channelId, id);
    for (var st : monthlySettlements) {
      if (s(st, "state").equals("DRAFT")) settlementMapper.updateForRefundSuccess(n(st, "id"));
      else d.issue("MONTHLY_REFUND", id, "已审批活动发生退款，请复核历史结算 " + n(st, "id"));
    }
  }
}
