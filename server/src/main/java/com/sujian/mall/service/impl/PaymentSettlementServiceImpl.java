package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;
import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSettlementServiceImpl implements PaymentSettlementService {
  private final BusinessSupport d;
  private final MemberMapper memberMapper;
  private final OrderItemMapper orderItemMapper;
  private final ShopOrderMapper shopOrderMapper;
  private final SkuMapper skuMapper;
  private final AssetService assets;
  public PaymentSettlementServiceImpl(BusinessSupport support, MemberMapper members,
      OrderItemMapper items, ShopOrderMapper orders, SkuMapper skus, AssetService assets) {
    d = support; memberMapper = members; orderItemMapper = items;
    shopOrderMapper = orders; skuMapper = skus; this.assets = assets;
  }

  @Transactional
  public void paid(String no, String transaction, long amount) {
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.lockOrderByNo(no));
    Problem.require(n(o, "cash_due") == amount, "PAYMENT_AMOUNT_MISMATCH", "渠道实付与订单金额不符");
    if (s(o, "payment_state").equals("SUCCESS")) {
      Problem.require(s(o, "transaction_id").equals(transaction), "PAYMENT_CONFLICT", "渠道交易单号冲突");
      return;
    }
    Problem.require(s(o, "state").equals("CREATED"), "PAYMENT_CONFLICT", "订单状态冲突，需人工核对");
    long uid = n(o, "member_id");
    com.sujian.mall.common.Rows.required(memberMapper.lockMemberId(uid));
    for (var i : orderItemMapper.listForPaid(no)) {
      Problem.require(
          skuMapper.confirmReservedStock(
                  n(i, "quantity"),
                  n(i, "quantity"),
                  n(i, "sku_id"),
                  n(i, "quantity"),
                  n(i, "quantity"))
              == 1,
          "STOCK_INCONSISTENT",
          "库存预占异常");
    }
    if (n(o, "points_used") > 0)
      assets.asset(uid, "points_held", -n(o, "points_used"), no + ":points-commit", no, "支付确认核销积分");
    shopOrderMapper.confirmPayment(transaction, d.now(), no);
    d.event(no + ":benefits", "BENEFITS", no);
  }

  @Transactional
  public void closeLocal(String no) {
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.lockOrderByNo(no));
    if (!s(o, "state").equals("CREATED")) return;
    long uid = n(o, "member_id");
    com.sujian.mall.common.Rows.required(memberMapper.lockMemberId(uid));
    for (var i : orderItemMapper.listForPaid(no))
      skuMapper.releaseReservedStock(n(i, "quantity"), n(i, "sku_id"), n(i, "quantity"));
    long p = n(o, "points_used");
    if (p > 0) {
      assets.asset(uid, "points_held", -p, no + ":points-unhold", no, "订单关闭解除冻结");
      assets.asset(uid, "points", p, no + ":points-return", no, "订单关闭退回积分");
    }
    shopOrderMapper.closeUnpaidOrder(no);
  }

}
