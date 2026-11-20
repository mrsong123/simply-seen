package com.sujian.mall.service.impl.channel;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.ChannelEvidenceService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ChannelEvidenceServiceImpl implements ChannelEvidenceService {
  private final RefundMapper refunds;
  private final WithdrawalMapper withdrawals;
  private final ShopOrderMapper orders;
  private final MemberMapper members;
  private final Settings settings;
  private final WechatClient channel;
  private static final Set<String> TRANSFER_STATES = Set.of(
      "ACCEPTED", "PROCESSING", "WAIT_USER_CONFIRM", "TRANSFERING", "SUCCESS", "FAIL", "CANCELING", "CANCELLED");

  public ChannelEvidenceServiceImpl(RefundMapper refunds, WithdrawalMapper withdrawals,
      ShopOrderMapper orders, MemberMapper members, Settings settings, WechatClient channel) {
    this.refunds = refunds;
    this.withdrawals = withdrawals;
    this.orders = orders;
    this.members = members;
    this.settings = settings;
    this.channel = channel;
  }

  @Override
  public Map<String, Object> refund(String no, Map<String, Object> supplied) {
    var refund = Rows.required(refunds.findForRefund(no));
    Problem.require(Set.of("REVIEWED", "PROCESSING", "ABNORMAL", "SUCCESS", "CLOSED").contains(s(refund, "state")),
        "REFUND_STATE", "退款尚未授权提交渠道");
    var order = Rows.required(orders.selectOrderByNo(s(refund, "order_id")));
    var proof = new LinkedHashMap<>(supplied);
    String notificationState = s(proof, "refund_status"), state = s(proof, "status");
    Problem.require(notificationState.isBlank() || state.isBlank() || notificationState.equals(state),
        "REFUND_MISMATCH", "退款通知状态字段冲突");
    if (!notificationState.isBlank()) proof.put("status", notificationState);
    verifyRefundContext(order, proof);
    verifyRefundIdentity(no, refund, proof);
    Map<?, ?> amount = amount(proof);
    Problem.require(!amount.containsKey("currency") || "CNY".equals(amount.get("currency")),
        "REFUND_MISMATCH", "退款币种不匹配");
    Problem.require(!amount.containsKey("total") || number(amount.get("total")) == n(order, "cash_due"),
        "REFUND_MISMATCH", "原订单支付金额与退款证据不匹配");
    // Official notifications may omit currency: obtain complete, signed original-order evidence.
    if (!amount.containsKey("currency") || !amount.containsKey("total")) {
      String notifiedRefundId = s(proof, "refund_id");
      proof = new LinkedHashMap<>(channel.queryRefund(no));
      verifyRefundContext(order, proof);
      verifyRefundIdentity(no, refund, proof);
      Problem.require(notifiedRefundId.isBlank() || notifiedRefundId.equals(s(proof, "refund_id")),
          "REFUND_CONFLICT", "退款通知与原单查询的渠道交易编号不一致");
      amount = amount(proof);
    }
    Problem.require("CNY".equals(amount.get("currency")) && number(amount.get("total")) == n(order, "cash_due"),
        "REFUND_MISMATCH", "退款渠道币种或原始金额不匹配");
    Problem.require(Set.of("SUCCESS", "PROCESSING", "CLOSED", "ABNORMAL").contains(s(proof, "status"))
            && !s(proof, "refund_id").isBlank(),
        "REFUND_EVIDENCE", "缺少可信退款状态或交易单号");
    if (!s(refund, "channel_id").isBlank())
      Problem.require(s(refund, "channel_id").equals(s(proof, "refund_id")),
          "REFUND_CONFLICT", "退款渠道交易编号与原回执不一致");
    return proof;
  }

  private void verifyRefundContext(Map<String, Object> order, Map<String, Object> proof) {
    // Query responses need not repeat the merchant identifier, but a supplied identifier cannot conflict.
    for (String field : List.of("mchid", "mch_id"))
      if (proof.containsKey(field))
        Problem.require(settings.required("WX_MCH_ID").equals(s(proof, field)),
            "CHANNEL_MISMATCH", "退款渠道商户号不匹配");
    if (proof.containsKey("transaction_id"))
      Problem.require(!s(order, "transaction_id").isBlank()
              && s(order, "transaction_id").equals(s(proof, "transaction_id")),
          "REFUND_MISMATCH", "退款渠道原支付交易编号不匹配");
  }

  private void verifyRefundIdentity(String no, Map<String, Object> refund, Map<String, Object> proof) {
    Problem.require(no.equals(s(proof, "out_refund_no"))
            && s(refund, "order_id").equals(s(proof, "out_trade_no"))
            && number(amount(proof).get("refund")) == n(refund, "amount"),
        "REFUND_MISMATCH", "退款渠道原单或金额不匹配");
  }

  private Map<?, ?> amount(Map<String, Object> proof) {
    if (proof.get("amount") instanceof Map<?, ?> amount) return amount;
    throw new Problem(409, "REFUND_MISMATCH", "退款渠道缺少金额证据");
  }

  private long number(Object value) {
    try { return Long.parseLong(Objects.toString(value, "")); }
    catch (NumberFormatException invalid) {
      throw new Problem(409, "CHANNEL_AMOUNT_INVALID", "渠道金额格式不正确");
    }
  }

  @Override
  public void transfer(String no, Map<String, Object> proof, boolean creationReceipt) {
    var withdrawal = Rows.required(withdrawals.findForTransfer(no));
    Problem.require(no.equals(s(proof, "out_bill_no"))
            && !s(proof, "transfer_bill_no").isBlank()
            && TRANSFER_STATES.contains(s(proof, "state")),
        "TRANSFER_MISMATCH", "渠道出款原单、交易编号或状态不匹配");
    if (!s(withdrawal, "channel_id").isBlank())
      Problem.require(s(withdrawal, "channel_id").equals(s(proof, "transfer_bill_no")),
          "TRANSFER_CONFLICT", "渠道出款交易编号与原回执不一致");
    if (!creationReceipt || proof.containsKey("transfer_amount"))
      Problem.require(number(proof.get("transfer_amount")) == n(withdrawal, "net"),
          "TRANSFER_MISMATCH", "渠道出款金额不匹配");
    if (!creationReceipt || proof.containsKey("mch_id"))
      Problem.require(settings.required("WX_MCH_ID").equals(s(proof, "mch_id")),
          "CHANNEL_MISMATCH", "渠道出款商户号不匹配");
    if (proof.containsKey("appid"))
      Problem.require(settings.required("WX_APP_ID").equals(s(proof, "appid")),
          "CHANNEL_MISMATCH", "渠道出款AppID不匹配");
    if (!creationReceipt || proof.containsKey("openid")) {
      String expected = s(Rows.required(members.selectAccountById(n(withdrawal, "member_id"))), "openid");
      Problem.require(!expected.isBlank() && expected.equals(s(proof, "openid")),
          "TRANSFER_RECIPIENT_MISMATCH", "渠道收款人与原提现会员不匹配");
    }
  }
}
