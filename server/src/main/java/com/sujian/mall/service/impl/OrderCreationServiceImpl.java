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
public class OrderCreationServiceImpl implements OrderCreationService {
  @org.springframework.beans.factory.annotation.Autowired private CartItemMapper cartItemMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private OrderItemMapper orderItemMapper;
  @org.springframework.beans.factory.annotation.Autowired private QuoteMapper quoteMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;
  @org.springframework.beans.factory.annotation.Autowired private SkuMapper skuMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final IdempotencyService idempotency;
  private final AssetService assets;
  private final PaymentService payments;
  private final OrderQueryService queries;

  public OrderCreationServiceImpl(
      BusinessSupport d,
      Settings c,
      IdempotencyService idempotency,
      AssetService assets,
      PaymentService payments,
      OrderQueryService queries) {
    this.d = d;
    this.c = c;
    this.idempotency = idempotency;
    this.assets = assets;
    this.payments = payments;
    this.queries = queries;
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public Object create(long uid, String key, Map<String, Object> b) {
    return idempotency.idem(
        uid,
        "order",
        key,
        b,
        () -> {
          var qr =
              com.sujian.mall.common.Rows.required(quoteMapper.findForCreate(s(b, "quoteId"), uid));
          Problem.require(n(qr, "expires_at") > d.now(), "QUOTE_EXPIRED", "报价已过期，请重新结算");
          var q = d.decode(s(qr, "payload"));
          Problem.require(n(qr, "address_id") == n(b, "addressId"), "QUOTE_EXPIRED", "地址已变更，请重新结算");
          var lines = (List<Map<String, Object>>) q.get("items");
          long points = n(q, "pointsUsed");
          var m = com.sujian.mall.common.Rows.required(memberMapper.lockAccountById(uid));
          Problem.require(n(m, "points") >= points, "POINTS_NOT_ENOUGH", "积分余额不足");
          for (var l : lines) {
            var sku =
                com.sujian.mall.common.Rows.required(
                    skuMapper.selectSkuForReservation(n(l, "skuId")));
            Problem.require(
                s(sku, "status").equals("ON_SALE")
                    && n(sku, "price") == n(l, "unitPrice")
                    && n(sku, "cost") == n(l, "cost")
                    && n(sku, "commission_percent") == n(l, "commissionPercent")
                    && n(l, "points") * 100 <= n(l, "amount") * n(sku, "points_percent") / 100,
                "QUOTE_EXPIRED",
                "商品或价格已变更，请重新报价");
            Problem.require(
                skuMapper.reserveStock(n(l, "quantity"), n(l, "skuId"), n(l, "quantity")) == 1,
                "STOCK_NOT_ENOUGH",
                "库存不足");
          }
          String no = d.no("SJ");
          long now = d.now();
          shopOrderMapper.insertPendingOrder(
              no,
              uid,
              n(q, "itemAmountFen"),
              n(q, "freightFen"),
              points,
              n(q, "cashDueFen"),
              d.encode(q.get("address")),
              d.encode(q.get("rule")),
              s(b, "buyerRemark").substring(0, Math.min(240, s(b, "buyerRemark").length())),
              now + c.integer("ORDER_EXPIRE_MINUTES", 15) * 60000L,
              now);
          for (var l : lines)
            orderItemMapper.insertForCreate(
                d.id(),
                no,
                n(l, "skuId"),
                n(l, "productId"),
                s(l, "name"),
                s(l, "image"),
                n(l, "quantity"),
                n(l, "unitPrice"),
                n(l, "cost"),
                n(l, "cash"),
                n(l, "points"),
                n(l, "commissionPercent"));
          if (points > 0) {
            assets.asset(uid, "points", -points, no + ":points-hold", no, "订单积分预占");
            assets.asset(uid, "points_held", points, no + ":points-held", no, "订单积分冻结");
          }
          for (var l : lines) cartItemMapper.deleteForCart(uid, n(l, "skuId"));
          quoteMapper.deleteForCreate(s(b, "quoteId"));
          if (n(q, "cashDueFen") == 0) payments.paid(no, "POINTS-" + no, 0);
          return queries.order(uid, no);
        });
  }
}
