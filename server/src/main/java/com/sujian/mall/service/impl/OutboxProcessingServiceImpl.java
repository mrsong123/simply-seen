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

  @org.springframework.beans.factory.annotation.Autowired private IdentityService identities;
  @org.springframework.beans.factory.annotation.Autowired private ChannelEvidenceService evidence;

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
        if (pending(kind, no)) {
          outboxMapper.updateForRun3(d.now() + 30000, "", id);
          continue;
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
    Problem.require(List.of("REVIEWED", "PROCESSING", "ABNORMAL").contains(s(r, "state")),
        "REFUND_STATE", "退款单尚未授权提交渠道");
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.selectOrderByNo(s(r, "order_id")));
    if (c.dev() || n(r, "amount") == 0) {
      finance.refundSuccess(no, "LOCAL-" + no);
      return;
    }
    refundMapper.updateForRefund(no);
    Map<String, Object> result;
    if (attempt == 0) result = w.refund(r, o);
    else {
      try { result = w.queryRefund(no); }
      catch (Problem failure) {
        if (!notFound(failure)) throw failure;
        result = w.refund(r, o); // Same merchant refund number: never create an unrelated retry.
      }
    }
    refundResult(no, result);

  }

  public void refundResult(String no, Map<String, Object> result) {
    var proof = evidence.refund(no, result);
    if (s(com.sujian.mall.common.Rows.required(refundMapper.findForRefund(no)), "state").equals("SUCCESS")) return;
    switch (s(proof, "status")) {
      case "SUCCESS" -> finance.refundSuccess(no, s(proof, "refund_id"));
      case "CLOSED" -> tx.executeWithoutResult(t -> {
        refundMapper.closeByChannel(no, s(proof, "refund_id"));
        d.issue("REFUND_CLOSED", no, "原渠道退款已明确关闭，未计为到账，可由会员重新提交售后");
      });
      case "ABNORMAL" -> tx.executeWithoutResult(t -> {
        refundMapper.updateForRefundResult(no);
        d.issue("REFUND_ABNORMAL", no, "渠道退款异常，请在原单核对处理，不得盲目重新出款");
      });
      default -> { /* PROCESSING remains durable and is polled by the existing outbox. */ }
    }
  }

  public void transfer(String no, long attempt) {
    var r = com.sujian.mall.common.Rows.required(withdrawalMapper.findForTransfer(no));
    if (List.of("SUCCESS", "FAILED", "REJECTED").contains(s(r, "state"))) return;
    if (c.dev()) throw new Problem(503, "DEV_CONFIRM_REQUIRED", "开发环境需显式确认测试出款");
    Problem.require(c.enabled("TRANSFER_ENABLED"), "TRANSFER_DISABLED", "真实出款未开放");
    Problem.require(List.of("SUBMITTED", "UNKNOWN", "CONFIRMING").contains(s(r, "state")),
        "TRANSFER_NOT_AUTHORIZED", "提现尚未经独立审核及执行授权");
    Map<String, Object> result;
    if (attempt == 0) result = submitTransfer(no, r);
    else {
      try {
        result = w.queryTransfer(no);
        evidence.transfer(no, result, false);
      } catch (Problem failure) {
        if (!notFound(failure)) throw failure;
        result = submitTransfer(no, r);
      }
      if (List.of("WAIT_USER_CONFIRM", "TRANSFERING").contains(s(result, "state"))
          && s(r, "package_info").isBlank() && s(result, "package_info").isBlank())
        result = submitTransfer(no, r); // Retrieve the original idempotent receipt after a lost response.
    }
    finance.transferResult(no, result);

  }

  private Map<String, Object> submitTransfer(String no, Map<String, Object> withdrawal) {
    var member = com.sujian.mall.common.Rows.required(memberMapper.selectAccountById(n(withdrawal, "member_id")));
    Problem.require(!b(member, "blocked") && n(member, "debt") == 0,
        "RISK_REVIEW", "提现账户存在风险事项，暂不提交新的渠道出款");
    String name = identities.verifiedNameForTransfer(n(withdrawal, "member_id"));
    var receipt = w.transfer(withdrawal, s(member, "openid"), name);
    evidence.transfer(no, receipt, true);
    if (List.of("SUCCESS", "FAIL", "CANCELLED").contains(s(receipt, "state"))) {
      var full = w.queryTransfer(no);
      evidence.transfer(no, full, false);
      return full;
    }
    return receipt;
  }

  private boolean pending(String kind, String no) {
    if (kind.equals("REFUND"))
      return !List.of("SUCCESS", "CLOSED").contains(s(
          com.sujian.mall.common.Rows.required(refundMapper.findForRefund2(no)), "state"));
    if (kind.equals("TRANSFER"))
      return !List.of("SUCCESS", "FAILED", "REJECTED").contains(s(
          com.sujian.mall.common.Rows.required(withdrawalMapper.findForTransfer2(no)), "state"));
    return false;
  }

  private boolean notFound(Problem failure) {
    return Set.of("WX_RESOURCE_NOT_EXISTS", "WX_NOT_FOUND", "WX_ORDER_NOT_EXIST", "WX_OUT_BILL_NO_NOT_FOUND")
        .contains(failure.code);
  }

  public void validateTransfer(String no, Map<String, Object> result) {
    evidence.transfer(no, result, false);
  }
}
