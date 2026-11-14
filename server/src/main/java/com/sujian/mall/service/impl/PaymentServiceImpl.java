package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private OrderItemMapper orderItemMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;
  @org.springframework.beans.factory.annotation.Autowired private SkuMapper skuMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final WechatClient w;
  private final AssetService assets;
  private final OrderQueryService queries;
  private final org.springframework.transaction.support.TransactionTemplate tx;

  public PaymentServiceImpl(
      BusinessSupport d,
      Settings c,
      WechatClient w,
      AssetService assets,
      OrderQueryService queries,
      org.springframework.transaction.support.TransactionTemplate tx) {
    this.d = d;
    this.c = c;
    this.w = w;
    this.assets = assets;
    this.queries = queries;
    this.tx = tx;
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

  public Object prepay(long uid, String no) {
    var o = queries.order(uid, no);
    Problem.require(
        s(o, "state").equals("CREATED") && n(o, "expires_at") > d.now(),
        "ORDER_STATE",
        "订单已支付或已过期");
    if (c.dev()) return Map.of("mode", "DEVELOPMENT", "orderNo", no);
    tx.executeWithoutResult(
        t -> {
          var locked =
              com.sujian.mall.common.Rows.required(shopOrderMapper.lockOwnedOrderByNo(no, uid));
          Problem.require(
              s(locked, "state").equals("CREATED") && n(locked, "expires_at") > d.now(),
              "ORDER_STATE",
              "订单不可支付");
          shopOrderMapper.markPrepay(no);
        });
    var result =
        w.prepay(
            o,
            s(
                com.sujian.mall.common.Rows.required(memberMapper.selectOpenidByMemberId(uid)),
                "openid"));
    shopOrderMapper.markPrepayIfUnconfirmed(no);
    return result;
  }

  public void synchronizePayment(String no) {
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.selectOrderByNo(no));
    if (s(o, "payment_state").equals("SUCCESS")) return;
    var result = w.queryPayment(no);
    tx.executeWithoutResult(t -> paymentEvidence(result));
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void paymentEvidence(Map<String, Object> r) {
    Problem.require(
        s(r, "mchid").equals(c.required("WX_MCH_ID"))
            && s(r, "appid").equals(c.required("WX_APP_ID")),
        "CHANNEL_MISMATCH",
        "商户或小程序编号不符");
    if (s(r, "trade_state").equals("SUCCESS")) {
      Problem.require(!s(r, "transaction_id").isBlank(), "PAYMENT_EVIDENCE", "缺少可信渠道交易单号");
      var target =
          com.sujian.mall.common.Rows.required(
              shopOrderMapper.selectOrderOwner(s(r, "out_trade_no")));
      var payer = (Map<String, Object>) r.get("payer");
      Problem.require(
          payer != null
              && s(payer, "openid")
                  .equals(
                      s(
                          com.sujian.mall.common.Rows.required(
                              memberMapper.selectOpenidByMemberId(n(target, "member_id"))),
                          "openid")),
          "PAYMENT_PAYER_MISMATCH",
          "渠道付款人与订单归属不符");
      var amount = (Map<String, Object>) r.get("amount");
      Problem.require(s(amount, "currency").equals("CNY"), "CURRENCY_MISMATCH", "支付币种不符");
      paid(s(r, "out_trade_no"), s(r, "transaction_id"), n(amount, "total"));
    }
  }

  public void cancel(long uid, String no) {
    var o = queries.order(uid, no);
    if (s(o, "state").equals("CLOSED")) return;
    Problem.require(s(o, "state").equals("CREATED"), "ORDER_STATE", "已支付订单请申请售后");
    boolean closed =
        Boolean.TRUE.equals(
            tx.execute(
                t -> {
                  var locked =
                      com.sujian.mall.common.Rows.required(
                          shopOrderMapper.lockOwnedOrderByNo(no, uid));
                  if (!s(locked, "state").equals("CREATED"))
                    return s(locked, "state").equals("CLOSED");
                  if (c.dev() || s(locked, "payment_state").equals("INIT")) {
                    closeLocal(no);
                    return true;
                  }
                  return false;
                }));
    if (closed) return;
    synchronizePayment(no);
    o = queries.order(uid, no);
    Problem.require(s(o, "state").equals("CREATED"), "ORDER_PAID", "订单已支付");
    w.close(no);
    tx.executeWithoutResult(t -> closeLocal(no));
  }
}
