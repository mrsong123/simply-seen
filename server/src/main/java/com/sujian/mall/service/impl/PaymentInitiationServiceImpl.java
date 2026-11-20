package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.MemberMapper;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PaymentInitiationServiceImpl implements PaymentInitiationService {
  private final OrderQueryService queries;
  private final MemberMapper members;
  private final Settings settings;
  private final BusinessSupport support;
  private final WechatClient wechat;
  private final PrepaymentTransactions transactions;
  private final PaymentEvidenceService evidence;
  private final PaymentSettlementService settlement;

  public PaymentInitiationServiceImpl(OrderQueryService queries, MemberMapper members,
      Settings settings, BusinessSupport support, WechatClient wechat,
      PrepaymentTransactions transactions, PaymentEvidenceService evidence,
      PaymentSettlementService settlement) {
    this.queries = queries; this.members = members; this.settings = settings; this.support = support;
    this.wechat = wechat; this.transactions = transactions; this.evidence = evidence; this.settlement = settlement;
  }

  public Object prepay(long memberId, String no, String requestedChannel, String ip) {
    String channel = requestedChannel == null ? "MINI_PROGRAM" : requestedChannel;
    if (!Set.of("MINI_PROGRAM", "NATIVE", "H5").contains(channel))
      throw new Problem(400, "PAYMENT_CHANNEL", "支付方式不受支持");
    var order = queries.order(memberId, no);
    PrepaymentTransactions.requirePayable(order, support.now());
    if (settings.dev()) return Map.of("mode", "DEVELOPMENT", "orderNo", no);
    PrepaymentTransactions.requireChannel(order, channel);
    String openid = "";
    if (channel.equals("MINI_PROGRAM")) {
      openid = s(Rows.required(members.selectAccountById(memberId)), "openid");
      Problem.require(!openid.isBlank() && !openid.startsWith("dev:"), "WECHAT_BINDING_REQUIRED",
          "请在小程序使用同一手机号登录并绑定微信后支付");
    }
    var reservation = transactions.reserve(memberId, no, channel);
    if (reservation.cached() != null)
      return channel.equals("MINI_PROGRAM")
          ? wechat.refreshMiniPrepay(reservation.cached(), s(reservation.order(), "payment_app_id"))
          : reservation.cached();
    try {
      if (reservation.recover()) recover(no);
      var result = wechat.prepay(reservation.order(), openid, channel, ip);
      transactions.complete(no, reservation.attemptId(), result);
      return result;
    } catch (RuntimeException failure) {
      transactions.failed(no, reservation.attemptId());
      throw failure;
    }
  }

  private void recover(String no) {
    Map<String, Object> result;
    try { result = wechat.queryPayment(no); }
    catch (Problem error) {
      if (isMissingOrder(error)) return;
      throw error;
    }
    Problem.require(no.equals(s(result, "out_trade_no")), "PAYMENT_ORDER_MISMATCH", "渠道订单编号不符");
    evidence.apply(result);
    switch (s(result, "trade_state")) {
      case "SUCCESS" -> {
        throw new Problem(409, "ORDER_PAID", "订单已付款，请刷新订单");
      }
      case "CLOSED", "REVOKED" -> {
        settlement.closeLocal(no);
        throw new Problem(409, "ORDER_STATE", "渠道订单已关闭，请重新下单");
      }
      case "NOTPAY" -> { }
      default -> throw new Problem(409, "PAYMENT_PROCESSING", "渠道正在处理原支付，请稍后查询结果");
    }
  }

  static boolean isMissingOrder(Problem error) {
    return Set.of("WX_ORDER_NOT_EXIST", "WX_RESOURCE_NOT_EXISTS", "WX_RESOURCE_NOT_EXIST").contains(error.code);
  }
}
