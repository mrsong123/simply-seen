package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentEvidenceServiceImpl implements PaymentEvidenceService {
  private final ShopOrderMapper orders;
  private final MemberMapper members;
  private final Settings settings;
  private final WechatClient wechat;
  private final PaymentSettlementService settlement;
  private final TransactionTemplate tx;

  public PaymentEvidenceServiceImpl(ShopOrderMapper orders, MemberMapper members, Settings settings,
      WechatClient wechat, PaymentSettlementService settlement, TransactionTemplate transaction) {
    this.orders = orders; this.members = members; this.settings = settings;
    this.wechat = wechat; this.settlement = settlement; tx = transaction;
  }

  public void synchronize(String no) {
    var order = Rows.required(orders.selectOrderByNo(no));
    String state = s(order, "payment_state");
    if (state.equals("SUCCESS") || state.equals("INIT")) return;
    var result = wechat.queryPayment(no);
    Problem.require(no.equals(s(result, "out_trade_no")), "PAYMENT_ORDER_MISMATCH", "渠道订单编号不符");
    tx.executeWithoutResult(status -> apply(result));
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void apply(Map<String, Object> result) {
    var order = Rows.required(orders.lockOrderByNo(s(result, "out_trade_no")));
    String channel = s(order, "payment_channel");
    PaymentChannel selected;
    try { selected = PaymentChannel.valueOf(channel); }
    catch (IllegalArgumentException invalid) { throw new Problem(409, "PAYMENT_CHANNEL_MISMATCH", "订单尚未发起可信预支付"); }
    String appId = s(order, "payment_app_id");
    if (appId.isBlank()) appId = selected.appId(settings);
    Problem.require(s(result, "mchid").equals(settings.required("WX_MCH_ID")) && appId.equals(s(result, "appid")),
        "CHANNEL_MISMATCH", "商户或支付应用编号不符");
    if (!s(result, "trade_state").equals("SUCCESS")) return;
    String transaction = s(result, "transaction_id");
    Problem.require(!transaction.isBlank(), "PAYMENT_EVIDENCE", "缺少可信渠道交易单号");
    Problem.require(selected.tradeType().equals(s(result, "trade_type")),
        "PAYMENT_CHANNEL_MISMATCH", "渠道支付方式与订单预付记录不符");
    var payer = result.get("payer") instanceof Map<?, ?> ? (Map<String, Object>) result.get("payer") : null;
    String actualPayer = payer == null ? "" : s(payer, "openid");
    Problem.require(!actualPayer.isBlank(), "PAYMENT_PAYER_MISMATCH", "缺少可信付款人证据");
    if (channel.equals("MINI_PROGRAM")) {
      var member = Rows.required(members.selectAccountById(n(order, "member_id")));
      Problem.require(actualPayer.equals(s(member, "openid")),
          "PAYMENT_PAYER_MISMATCH", "渠道付款人与订单归属不符");
    }
    String originalPayer = s(order, "payment_payer_openid");
    Problem.require(originalPayer.isBlank() || originalPayer.equals(actualPayer),
        "PAYMENT_PAYER_MISMATCH", "渠道付款人与原交易证据不符");
    var amount = result.get("amount") instanceof Map<?, ?> ? (Map<String, Object>) result.get("amount") : null;
    Problem.require(amount != null && "CNY".equals(s(amount, "currency")),
        "CURRENCY_MISMATCH", "支付币种不符");
    settlement.paid(s(order, "id"), transaction, n(amount, "total"));
    orders.recordPaymentPayer(s(order, "id"), actualPayer);
  }
}
