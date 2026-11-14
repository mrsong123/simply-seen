package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundApplicationServiceImpl implements RefundApplicationService {
  @org.springframework.beans.factory.annotation.Autowired private OrderItemMapper orderItemMapper;
  @org.springframework.beans.factory.annotation.Autowired private RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final CommerceService shop;

  public RefundApplicationServiceImpl(BusinessSupport d, Settings c, CommerceService shop) {
    this.d = d;
    this.c = c;
    this.shop = shop;
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public Object refund(long uid, String key, Map<String, Object> b) {
    return shop.idem(
        uid,
        "refund",
        key,
        b,
        () -> {
          String no = s(b, "orderNo");
          var o = com.sujian.mall.common.Rows.required(shopOrderMapper.lockOwnedOrderByNo(no, uid));
          Problem.require(
              s(o, "payment_state").equals("SUCCESS") && !s(o, "state").equals("REFUNDED"),
              "ORDER_STATE",
              "订单不满足退款条件");
          Problem.require(refundMapper.countForShip(no) == 0, "REFUND_PENDING", "已有进行中的售后，请等待处理");
          String reason = s(b, "reason").trim();
          Problem.require(
              reason.length() >= 2 && reason.length() <= 240, "INVALID_INPUT", "退款原因需为2至240字");
          var requested = (List<Map<String, Object>>) b.get("lines");
          Problem.require(requested != null && !requested.isEmpty(), "INVALID_INPUT", "请选择退款商品");
          Set<Long> seen = new HashSet<>();
          var lines = new ArrayList<Map<String, Object>>();
          long cash = 0, points = 0, qty = 0;
          for (var r : requested) {
            long id = n(r, "orderItemId"), q = n(r, "quantity");
            Problem.require(seen.add(id) && q > 0, "INVALID_QUANTITY", "退款商品重复或数量无效");
            var i = com.sujian.mall.common.Rows.required(orderItemMapper.findForRefund(id, no));
            long available = n(i, "quantity") - n(i, "refunded_quantity");
            Problem.require(q <= available, "REFUND_AMOUNT_EXCEEDED", "退款数量超过剩余可退数量");
            long rc =
                q == available
                    ? n(i, "cash_amount") - n(i, "refunded_cash")
                    : n(i, "cash_amount") * q / n(i, "quantity");
            long rp =
                q == available
                    ? n(i, "points_amount") - n(i, "refunded_points")
                    : n(i, "points_amount") * q / n(i, "quantity");
            lines.add(Map.of("id", id, "quantity", q, "cash", rc, "points", rp));
            cash += rc;
            points += rp;
            qty += q;
          }
          long remain = orderItemMapper.countForRefund(no);
          if (qty == remain) cash += n(o, "freight");
          Problem.require(
              cash + n(o, "cash_refunded") <= n(o, "cash_due")
                  && points + n(o, "points_refunded") <= n(o, "points_used"),
              "REFUND_AMOUNT_EXCEEDED",
              "累计退款超过原始实付");
          String id = d.no("RF");
          refundMapper.insertForRefund(
              id, no, uid, cash, points, d.encode(lines), reason, uid, d.now());
          return Map.of("id", id, "state", "REQUESTED");
        });
  }

  @Transactional
  public void reviewRefund(long actor, String id, boolean approve, String reason) {
    var r = com.sujian.mall.common.Rows.required(refundMapper.findForReviewRefund(id));
    Problem.require(s(r, "state").equals("REQUESTED"), "REFUND_STATE", "退款单已处理");
    if (!approve) {
      Problem.require(!reason.isBlank(), "REASON_REQUIRED", "请填写驳回原因");
      refundMapper.updateForReviewRefund(actor, reason, id);
    } else {
      refundMapper.updateForReviewRefund2(actor, id);
      d.event(id + ":refund", "REFUND", id);
    }
    d.audit(actor, approve ? "REFUND_APPROVE" : "REFUND_REJECT", id, Map.of("reason", reason));
  }
}
