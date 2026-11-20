package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;
import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.ShopOrderMapper;
import com.sujian.mall.mapper.PaymentSessionMapper;
import com.sujian.mall.service.*;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentCancellationServiceImpl implements PaymentCancellationService {
  private final OrderQueryService queries;
  private final Settings c;
  private final ShopOrderMapper shopOrderMapper;
  private final WechatClient w;
  private final PaymentEvidenceService evidence;
  private final PaymentSettlementService settlement;
  private final TransactionTemplate tx;
  private final PaymentSessionMapper sessions;
  public PaymentCancellationServiceImpl(OrderQueryService queries, Settings settings,
      ShopOrderMapper orders, WechatClient wechat, PaymentEvidenceService evidence,
      PaymentSettlementService settlement, TransactionTemplate transaction, PaymentSessionMapper sessions) {
    this.queries = queries; c = settings; shopOrderMapper = orders; w = wechat;
    this.evidence = evidence; this.settlement = settlement; tx = transaction; this.sessions = sessions;
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
                    settlement.closeLocal(no);
                    return true;
                  }
                  var session = sessions.selectById(no);
                  if (session != null && "CREATING".equals(session.getState())
                      && session.getLeaseUntil() > System.currentTimeMillis())
                    throw new Problem(409, "PAYMENT_PREPARING", "支付请求正在处理，请稍后查询或取消");
                  shopOrderMapper.markPaymentClosing(no);
                  return false;
                }));
    if (closed) return;
    Map<String, Object> result;
    try { result = w.queryPayment(no); }
    catch (Problem error) {
      if (!PaymentInitiationServiceImpl.isMissingOrder(error)) throw error;
      tx.executeWithoutResult(status -> settlement.closeLocal(no));
      return;
    }
    evidence.apply(result);
    if (s(result, "trade_state").equals("CLOSED") || s(result, "trade_state").equals("REVOKED")) {
      tx.executeWithoutResult(status -> settlement.closeLocal(no));
      return;
    }
    o = queries.order(uid, no);
    Problem.require(s(o, "state").equals("CREATED"), "ORDER_PAID", "订单已支付");
    Problem.require(s(result, "trade_state").equals("NOTPAY") || s(result, "trade_state").equals("USERPAYING"),
        "CHANNEL_UNKNOWN", "渠道状态尚未确认，请稍后查询，库存和积分仍保留");
    w.close(no);
    tx.executeWithoutResult(t -> settlement.closeLocal(no));
  }
}
