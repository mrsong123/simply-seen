package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.Problem;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.job.OutboxJob;
import com.sujian.mall.service.*;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WechatCallbackServiceImpl implements WechatCallbackService {
  private final WechatClient channel;
  private final CommerceService commerce;
  private final FinanceService finance;
  private final OutboxJob jobs;

  public WechatCallbackServiceImpl(
      WechatClient channel, CommerceService commerce, FinanceService finance, OutboxJob jobs) {
    this.channel = channel;
    this.commerce = commerce;
    this.finance = finance;
    this.jobs = jobs;
  }

  public Map<String, String> handle(
      String type, String body, String serial, String timestamp, String nonce, String signature) {
    channel.verify(serial, timestamp, nonce, signature, body);
    var payload = channel.decrypt(body);
    switch (type) {
      case "pay" -> commerce.paymentEvidence(payload);
      case "refund" -> jobs.refundResult(s(payload, "out_refund_no"), payload);
      case "transfer" -> {
        String id = s(payload, "out_bill_no");
        jobs.validateTransfer(id, payload);
        finance.transferResult(id, payload);
      }
      default -> throw new Problem(404, "NOT_FOUND", "回调不存在");
    }
    return Map.of("code", "SUCCESS", "message", "成功");
  }
}
