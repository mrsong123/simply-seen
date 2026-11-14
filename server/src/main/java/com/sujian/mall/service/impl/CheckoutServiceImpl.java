package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutServiceImpl implements CheckoutService {
  @org.springframework.beans.factory.annotation.Autowired private AddressMapper addressMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private QuoteMapper quoteMapper;
  @org.springframework.beans.factory.annotation.Autowired private SkuMapper skuMapper;

  private final BusinessSupport d;
  private final CatalogService catalog;

  public CheckoutServiceImpl(BusinessSupport d, CatalogService catalog) {
    this.d = d;
    this.catalog = catalog;
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public Map<String, Object> quote(long uid, Map<String, Object> b) {
    var m = com.sujian.mall.common.Rows.required(memberMapper.selectAccountById(uid));
    long address = n(b, "addressId");
    var addr = com.sujian.mall.common.Rows.required(addressMapper.findForQuote(address, uid));
    List<Map<String, Object>> items = (List<Map<String, Object>>) b.get("items");
    Problem.require(
        items != null && !items.isEmpty() && items.size() <= 50, "INVALID_ITEMS", "请选择1至50种商品");
    var rule = catalog.rules();
    TreeMap<Long, Integer> quantities = new TreeMap<>();
    for (var i : items) {
      int qty = Math.toIntExact(n(i, "quantity"));
      Problem.require(qty > 0 && qty <= 99, "INVALID_QUANTITY", "购买数量应为1至99");
      quantities.merge(n(i, "skuId"), qty, Integer::sum);
    }
    var lines = new ArrayList<Map<String, Object>>();
    long total = 0, maxPoints = 0;
    for (var entry : quantities.entrySet()) {
      long id = entry.getKey();
      int qty = entry.getValue();
      Problem.require(qty <= 99, "INVALID_QUANTITY", "每种规格最多99件");
      var s = com.sujian.mall.common.Rows.required(skuMapper.selectQuotedSku(id));
      Problem.require(
          s(s, "status").equals("ON_SALE") && n(s, "stock") - n(s, "held") >= qty,
          "STOCK_NOT_ENOUGH",
          "商品已下架或库存不足");
      long amount = Math.multiplyExact(n(s, "price"), qty);
      long allowed = amount * n(s, "points_percent") / 100 / 100;
      var line = new LinkedHashMap<String, Object>();
      line.put("skuId", id);
      line.put("productId", n(s, "product_id"));
      line.put("name", s(s, "product_name") + " · " + s(s, "name"));
      line.put("image", s(s, "image"));
      line.put("quantity", qty);
      line.put("unitPrice", n(s, "price"));
      line.put("cost", n(s, "cost"));
      line.put("commissionPercent", n(s, "commission_percent"));
      line.put("amount", amount);
      line.put("maxPoints", allowed);
      lines.add(line);
      total += amount;
      maxPoints += allowed;
    }
    long requested = n(b, "pointsRequested");
    Problem.require(
        requested >= 0 && requested <= maxPoints && requested <= n(m, "points"),
        "POINTS_NOT_ENOUGH",
        "积分不足或超过商品抵扣上限");
    long left = requested;
    for (var line : lines) {
      long points = Math.min(left, n(line, "maxPoints"));
      line.put("points", points);
      line.put("cash", n(line, "amount") - points * 100);
      left -= points;
    }
    long freight = total >= n(rule, "freeShippingFen") ? 0 : n(rule, "freightFen");
    long cash = total + freight - requested * 100;
    Problem.require(cash >= 0 && cash <= 100000000, "INVALID_AMOUNT", "订单金额超出支持范围");
    rule.put("beneficiaries", beneficiaries(m));
    String id = d.no("Q");
    var q = new LinkedHashMap<String, Object>();
    q.put("quoteId", id);
    q.put("items", lines);
    q.put("itemAmountFen", total);
    q.put("freightFen", freight);
    q.put("pointsUsed", requested);
    q.put("pointsAllowed", Math.min(maxPoints, n(m, "points")));
    q.put("cashDueFen", cash);
    q.put("address", addr);
    q.put("rule", rule);
    q.put("expiresAt", d.now() + 300000);
    quoteMapper.insertForQuote(id, uid, address, d.encode(q), n(q, "expiresAt"));
    return q;
  }

  private List<Map<String, Object>> beneficiaries(Map<String, Object> m) {
    var result = new ArrayList<Map<String, Object>>();
    long parent = n(m, "inviter_id");
    for (int level = 1; level <= 2 && parent != 0; level++) {
      var p = com.sujian.mall.common.Rows.required(memberMapper.selectAccountById(parent));
      if (b(p, "promoter") && !b(p, "blocked")) result.add(Map.of("id", parent, "level", level));
      parent = n(p, "inviter_id");
    }
    return result;
  }
}
