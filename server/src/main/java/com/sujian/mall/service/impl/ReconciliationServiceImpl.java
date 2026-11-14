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
public class ReconciliationServiceImpl implements ReconciliationService {
  @org.springframework.beans.factory.annotation.Autowired private LedgerMapper ledgerMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private ReconcileBatchMapper reconcileBatchMapper;

  @org.springframework.beans.factory.annotation.Autowired private RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;
  @org.springframework.beans.factory.annotation.Autowired private WithdrawalMapper withdrawalMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final CommerceService shop;
  private final WechatClient w;
  private final OutboxProcessingService processing;
  private final org.springframework.transaction.support.TransactionTemplate tx;

  public ReconciliationServiceImpl(
      BusinessSupport d,
      Settings c,
      CommerceService shop,
      WechatClient w,
      OutboxProcessingService processing,
      org.springframework.transaction.support.TransactionTemplate tx) {
    this.d = d;
    this.c = c;
    this.shop = shop;
    this.w = w;
    this.processing = processing;
    this.tx = tx;
  }

  public Map<String, Object> reconcile(long actor) {
    long diffs = 0;
    var members = memberMapper.selectAccountsForReconciliation();
    for (var m : members)
      for (String asset : List.of("points", "points_held", "available", "reserved", "debt")) {
        long actual = ledgerMapper.countForReconcile(n(m, "id"), asset);
        if (actual != n(m, asset)) {
          diffs++;
          String resource = n(m, "id") + ":" + asset;
          tx.executeWithoutResult(
              t -> d.issue("LEDGER_DIFF", resource, "余额与流水不符，差额 " + (n(m, asset) - actual)));
        }
      }
    for (var o : shopOrderMapper.selectPaidOrdersForReconciliation()) {
      if (c.dev()) continue;
      try {
        var r = w.queryPayment(s(o, "id"));
        if (!s(r, "trade_state").equals("SUCCESS")) {
          diffs++;
          tx.executeWithoutResult(t -> d.issue("CHANNEL_DIFF", s(o, "id"), "本地已支付与微信查询状态不符"));
        } else shop.paymentEvidence(r);
      } catch (Exception e) {
        diffs++;
        tx.executeWithoutResult(
            t -> d.issue("CHANNEL_UNVERIFIED", s(o, "id"), "本次渠道对账无法确认，不能视为账实一致"));
      }
    }
    if (!c.dev()) {
      for (var r : refundMapper.listForReconcile())
        try {
          var proof = w.queryRefund(s(r, "id"));
          processing.refundResult(s(r, "id"), proof);
          if (!s(proof, "status").equals("SUCCESS"))
            throw new Problem(409, "REFUND_CHANNEL_DIFF", "退款渠道不一致");
        } catch (Exception e) {
          diffs++;
          tx.executeWithoutResult(
              t -> d.issue("REFUND_CHANNEL_DIFF", s(r, "id"), "已退款记录与本次渠道核对结果不一致或无法确认"));
        }
      for (var r : withdrawalMapper.listForReconcile())
        try {
          var proof = w.queryTransfer(s(r, "id"));
          processing.validateTransfer(s(r, "id"), proof);
          if (!s(proof, "state").equals("SUCCESS"))
            throw new Problem(409, "TRANSFER_CHANNEL_DIFF", "出款渠道不一致");
        } catch (Exception e) {
          diffs++;
          tx.executeWithoutResult(
              t -> d.issue("TRANSFER_CHANNEL_DIFF", s(r, "id"), "本地已出款与本次渠道核对结果不一致或无法确认"));
        }
    }
    long id = d.id(), finalDiffs = diffs;
    tx.executeWithoutResult(
        t -> {
          reconcileBatchMapper.insertForReconcile(
              id,
              LocalDateText(),
              shopOrderMapper.sumConfirmedCash(),
              refundMapper.countForReconcile(),
              withdrawalMapper.countForReconcile(),
              finalDiffs,
              d.now());
          d.audit(
              actor,
              "RECONCILE",
              "" + id,
              Map.of("diffs", finalDiffs, "channelMode", c.dev() ? "DEVELOPMENT" : "WECHAT_QUERY"));
        });
    return com.sujian.mall.common.Rows.required(reconcileBatchMapper.findForReconcile(id));
  }

  private String LocalDateText() {
    return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
  }
}
