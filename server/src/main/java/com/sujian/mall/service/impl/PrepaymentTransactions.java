package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.entity.PaymentSession;
import com.sujian.mall.config.Settings;
import com.sujian.mall.mapper.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Database lease keeps network requests outside locks and fences late results after recovery. */
@Service
public class PrepaymentTransactions {
  public record Reservation(String attemptId, Map<String, Object> order,
      Map<String, Object> cached, boolean recover) {}
  private final ShopOrderMapper orders;
  private final PaymentSessionMapper sessions;
  private final BusinessSupport support;
  private final TransactionTemplate tx;
  private final Settings settings;

  public PrepaymentTransactions(ShopOrderMapper orders, PaymentSessionMapper sessions,
      BusinessSupport support, TransactionTemplate tx, Settings settings) {
    this.orders = orders; this.sessions = sessions; this.support = support; this.tx = tx; this.settings = settings;
  }

  public Reservation reserve(long memberId, String no, String channel) {
    return tx.execute(status -> {
      var order = Rows.required(orders.lockOwnedOrderByNo(no, memberId));
      requirePayable(order, support.now());
      requireChannel(order, channel);
      var session = sessions.selectById(no);
      if (session != null && "READY".equals(session.getState())
          && session.getExpiresAt() > support.now())
        return new Reservation(null, order, support.decode(session.getResponsePayload()), false);
      if (session != null && "CREATING".equals(session.getState())
          && session.getLeaseUntil() > support.now())
        throw new Problem(409, "PAYMENT_PREPARING", "支付请求正在处理，请稍后查询或重试");
      boolean recover = session != null || !s(order, "payment_state").equals("INIT");
      String attempt = UUID.randomUUID().toString();
      long now = support.now(), expiry = n(order, "expires_at");
      String appId = s(order, "payment_app_id");
      if (appId.isBlank()) appId = PaymentChannel.valueOf(channel).appId(settings);
      Problem.require(orders.markPrepayChannel(no, channel, appId) == 1,
          "PAYMENT_CLOSING", "订单正在取消，请查询结果");
      if (session == null) {
        session = new PaymentSession();
        session.setOrderId(no); session.setChannel(channel); session.setAttemptId(attempt);
        session.setState("CREATING"); session.setLeaseUntil(now + 45000);
        session.setExpiresAt(expiry); session.setUpdatedAt(now); sessions.insert(session);
      } else sessions.reserve(no, channel, attempt, now + 45000, expiry, now);
      order.put("payment_channel", channel);
      order.put("payment_app_id", appId);
      return new Reservation(attempt, order, null, recover);
    });
  }

  public void complete(String no, String attempt, Object result) {
    tx.executeWithoutResult(status -> {
      var order = Rows.required(orders.lockOrderByNo(no));
      requirePayable(order, support.now());
      Problem.require(sessions.ready(no, attempt, support.encode(result), support.now()) == 1,
          "PAYMENT_PREPARING", "支付请求状态已变化，请刷新后查询");
    });
  }

  public void failed(String no, String attempt) {
    tx.executeWithoutResult(status -> sessions.failed(no, attempt, "UNKNOWN", support.now()));
  }

  static void requirePayable(Map<String, Object> order, long now) {
    Problem.require("CREATED".equals(s(order, "state")) && n(order, "expires_at") > now,
        "ORDER_STATE", "订单已支付、已关闭或已过期，请刷新订单");
    Problem.require(!"CLOSING".equals(s(order, "payment_state")),
        "PAYMENT_CLOSING", "订单正在取消，请查询结果或继续取消");
  }

  static void requireChannel(Map<String, Object> order, String channel) {
    String selected = s(order, "payment_channel");
    Problem.require(selected.isBlank() || "UNSELECTED".equals(selected) || channel.equals(selected),
        "PAYMENT_CHANNEL_LOCKED", "此订单已发起其他方式支付，请使用原支付入口或取消后重新下单");
  }
}
