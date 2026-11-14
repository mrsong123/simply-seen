package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OutboxProcessingServiceImpl implements OutboxProcessingService {
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private OutboxMapper outboxMapper;
  @org.springframework.beans.factory.annotation.Autowired private RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;
  @org.springframework.beans.factory.annotation.Autowired private WithdrawalMapper withdrawalMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final CommerceService shop;
  private final FinanceService finance;
  private final WechatClient w;
  private final org.springframework.transaction.support.TransactionTemplate tx;

  public OutboxProcessingServiceImpl(
      BusinessSupport d,
      Settings c,
      CommerceService shop,
      FinanceService finance,
      WechatClient w,
      org.springframework.transaction.support.TransactionTemplate tx) {
    this.d = d;
    this.c = c;
    this.shop = shop;
    this.finance = finance;
    this.w = w;
    this.tx = tx;
  }

  public void run() {
    for (int i = 0; i < 20; i++) {
      var event = claim();
      if (event == null) break;
      String id = s(event, "id"), kind = s(event, "kind"), no = s(event, "aggregate_id");
      try {
        switch (kind) {
          case "BENEFITS" -> shop.benefits(no);
          case "REFUND" -> refund(no, n(event, "attempts"));
          case "TRANSFER" -> transfer(no, n(event, "attempts"));
          default -> throw new IllegalStateException("Unknown event");
        }
        outboxMapper.updateForRun(id);
      } catch (Exception ex) {
        if (ex instanceof Problem p
            && List.of("CONFIG_REQUIRED", "KEY_CONFIGURATION", "INSECURE_ENDPOINT", "SIGN_FAILED")
                .contains(p.code)) outboxMapper.updateForRun2(id);
        outboxMapper.updateForRun3(
            d.now() + Math.min(3600000, 30000L * (1L << Math.min(6, n(event, "attempts")))),
            ex instanceof Problem p ? p.code : "PROCESSING_ERROR",
            id);
        tx.executeWithoutResult(
            t ->
                d.issue(
                    "OUTBOX",
                    id,
                    "异步任务需要关注；错误码 " + (ex instanceof Problem p ? p.code : "PROCESSING_ERROR")));
      }
    }
  }

  private Map<String, Object> claim() {
    return tx.execute(
        t -> {
          var rows = outboxMapper.listForClaim(d.now());
          if (rows.isEmpty()) return null;
          var row = rows.getFirst();
          outboxMapper.updateForClaim(d.now() + 120000, s(row, "id"));
          return row;
        });
  }

  public void refund(String no, long attempt) {
    var r = com.sujian.mall.common.Rows.required(refundMapper.findForRefund(no));
    if (s(r, "state").equals("SUCCESS") || s(r, "state").equals("CLOSED")) return;
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.selectOrderByNo(s(r, "order_id")));
    if (c.dev() || n(r, "amount") == 0) {
      finance.refundSuccess(no, "LOCAL-" + no);
      return;
    }
    refundMapper.updateForRefund(no);
    Map<String, Object> result = attempt == 0 ? w.refund(r, o) : w.queryRefund(no);
    refundResult(no, result);
    if (!List.of("SUCCESS", "CLOSED")
        .contains(
            s(com.sujian.mall.common.Rows.required(refundMapper.findForRefund2(no)), "state")))
      throw new Problem(503, "REFUND_PENDING", "退款待确认");
  }

  @SuppressWarnings("unchecked")
  public void refundResult(String no, Map<String, Object> result) {
    var r = com.sujian.mall.common.Rows.required(refundMapper.findForRefund(no));
    var a = (Map<String, Object>) result.get("amount");
    Problem.require(
        s(result, "out_refund_no").equals(no)
            && s(result, "out_trade_no").equals(s(r, "order_id"))
            && a != null
            && n(a, "refund") == n(r, "amount")
            && s(a, "currency").equals("CNY"),
        "REFUND_MISMATCH",
        "退款渠道证据不匹配");
    if (s(result, "status").equals("SUCCESS")) finance.refundSuccess(no, s(result, "refund_id"));
    else if (List.of("ABNORMAL", "CLOSED").contains(s(result, "status"))) {
      refundMapper.updateForRefundResult(no);
      tx.executeWithoutResult(t -> d.issue("REFUND_ABNORMAL", no, "渠道退款异常，请人工核对，不得以新单号重复退款"));
    }
  }

  public void transfer(String no, long attempt) {
    var r = com.sujian.mall.common.Rows.required(withdrawalMapper.findForTransfer(no));
    if (List.of("SUCCESS", "FAILED", "REJECTED").contains(s(r, "state"))) return;
    if (c.dev()) throw new Problem(503, "DEV_CONFIRM_REQUIRED", "开发环境需显式确认测试出款");
    Problem.require(c.enabled("TRANSFER_ENABLED"), "TRANSFER_DISABLED", "真实出款未开放");
    var result =
        attempt == 0
            ? w.transfer(
                r,
                s(
                    com.sujian.mall.common.Rows.required(
                        memberMapper.selectOpenidByMemberId(n(r, "member_id"))),
                    "openid"))
            : w.queryTransfer(no);
    validateTransfer(no, result);
    finance.transferResult(no, result);
    if (!List.of("SUCCESS", "FAILED")
        .contains(
            s(
                com.sujian.mall.common.Rows.required(withdrawalMapper.findForTransfer2(no)),
                "state"))) throw new Problem(503, "TRANSFER_UNKNOWN", "转账尚无最终结果");
  }

  public void validateTransfer(String no, Map<String, Object> result) {
    var r = com.sujian.mall.common.Rows.required(withdrawalMapper.findForTransfer(no));
    Problem.require(
        s(result, "out_bill_no").equals(no) && n(result, "transfer_amount") == n(r, "net"),
        "TRANSFER_MISMATCH",
        "渠道出款单号或金额不匹配");
    if (result.containsKey("appid"))
      Problem.require(
          s(result, "appid").equals(c.required("WX_APP_ID")), "CHANNEL_MISMATCH", "出款AppID不匹配");
  }
}
