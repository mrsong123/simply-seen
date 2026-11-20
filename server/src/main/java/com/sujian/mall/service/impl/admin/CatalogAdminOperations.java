package com.sujian.mall.service.impl.admin;

import static com.sujian.mall.common.BusinessSupport.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sujian.mall.common.BusinessSupport;
import com.sujian.mall.common.Problem;
import com.sujian.mall.entity.Category;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.CommerceService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogAdminOperations {
  @org.springframework.beans.factory.annotation.Autowired private CategoryMapper categoryMapper;
  @org.springframework.beans.factory.annotation.Autowired private ProductMapper productMapper;
  @org.springframework.beans.factory.annotation.Autowired private SkuMapper skuMapper;

  private final BusinessSupport d;
  private final CommerceService shop;

  public CatalogAdminOperations(BusinessSupport d, CommerceService shop) {
    this.d = d;
    this.shop = shop;
  }

  public Object categories() {
    return categoryMapper
        .selectList(
            Wrappers.<Category>lambdaQuery().orderByAsc(Category::getSortNo, Category::getId))
        .stream()
        .map(row -> d.decode(d.encode(row)))
        .toList();
  }

  public Object rules() {
    var r = shop.rules();
    return Map.of(
        "version",
        r.get("version"),
        "pointsPer100Yuan",
        r.get("pointsPer100Yuan"),
        "freightFen",
        r.get("freightFen"),
        "freeShippingFen",
        r.get("freeShippingFen"),
        "settlementDays",
        r.get("settlementDays"),
        "level1Bps",
        r.get("level1Bps"),
        "level2Bps",
        r.get("level2Bps"),
        "distributionEnabled",
        r.get("distributionEnabled"));
  }

  public Object adminProducts() {
    var rows = productMapper.listForAdminProducts();
    for (var p : rows) {
      p.put("skus", skuMapper.selectCatalogSkus(n(p, "id")));
      p.put("retired_skus", skuMapper.selectRetiredSkus(n(p, "id")));
    }
    return rows;
  }

  @Transactional
  public Object saveProduct(long actor, Map<String, Object> b) {
    String name = s(b, "name"), image = s(b, "image"), status = s(b, "status");
    Problem.require(
        name.length() > 0
            && name.length() <= 120
            && s(b, "description").length() <= 20000
            && (image.startsWith("https://") || image.matches("/art/[a-z]+\\.svg"))
            && List.of("ON_SALE", "OFF_SALE").contains(status)
            && n(b, "points_percent") >= 0
            && n(b, "points_percent") <= 100
            && n(b, "commission_percent") >= 0
            && n(b, "commission_percent") <= 10
            && s(b, "color").matches("#[0-9a-fA-F]{6}"),
        "INVALID_PRODUCT",
        "商品信息不完整，图片需HTTPS，佣金上限≤10%");
    com.sujian.mall.common.Rows.required(categoryMapper.findForSaveProduct(n(b, "category_id")));
    long id = n(b, "id");
    if (id == 0) {
      id = d.id();
      productMapper.insertForSaveProduct(
          id,
          n(b, "category_id"),
          name,
          s(b, "subtitle"),
          s(b, "description"),
          image,
          s(b, "color"),
          status,
          b(b, "featured"),
          n(b, "points_percent"),
          n(b, "commission_percent"),
          d.now());
    } else {
      com.sujian.mall.common.Rows.required(productMapper.findForSaveProduct(id));
      productMapper.updateForSaveProduct(
          n(b, "category_id"),
          name,
          s(b, "subtitle"),
          s(b, "description"),
          image,
          s(b, "color"),
          status,
          b(b, "featured"),
          n(b, "points_percent"),
          n(b, "commission_percent"),
          id);
    }
    @SuppressWarnings("unchecked")
    var skus = (List<Map<String, Object>>) b.get("skus");
    Problem.require(
        skus != null && !skus.isEmpty() && skus.size() <= 20, "INVALID_SKU", "需配置1至20个规格");
    var previous = skuMapper.lockActiveCatalogSkus(id);
    Set<Long> retained = new HashSet<>();
    Set<String> codes = new HashSet<>();
    for (var s : skus) {
      Problem.require(codes.add(s(s, "code")), "INVALID_SKU", "商品规格编码不能重复");
      Problem.require(
          n(s, "price") > 0
              && n(s, "price") <= 10000000
              && n(s, "cost") >= 0
              && n(s, "cost") <= n(s, "price")
              && n(s, "stock") >= 0
              && n(s, "stock") <= 1000000
              && !s(s, "name").isBlank()
              && !s(s, "code").isBlank(),
          "INVALID_SKU",
          "规格价格、成本或库存无效");
      long sid = n(s, "id");
      Problem.require(sid == 0 || retained.add(sid), "INVALID_SKU", "商品规格不能重复");
      if (sid == 0)
        skuMapper.insertCatalogSku(
            d.id(), id, s(s, "name"), s(s, "code"), n(s, "price"), n(s, "cost"), n(s, "stock"));
      else {
        var old = com.sujian.mall.common.Rows.required(skuMapper.lockSkuForCatalogUpdate(sid, id));
        Problem.require(n(s, "stock") >= n(old, "held"), "STOCK_HELD", "库存不可低于已预占数量");
        skuMapper.updateCatalogSku(
            s(s, "name"), s(s, "code"), n(s, "price"), n(s, "cost"), n(s, "stock"), sid);
      }
    }
    for (var old : previous) {
      if (retained.contains(n(old, "id"))) continue;
      Problem.require(n(old, "held") == 0, "STOCK_HELD", "有订单预占的规格不能移除，请等待付款或取消");
      Problem.require(skuMapper.retireCatalogSku(n(old, "id")) == 1, "STOCK_HELD", "规格库存状态已变化，请刷新重试");
    }
    d.audit(actor, "PRODUCT_SAVE", "" + id, Map.of("name", name, "status", status));
    return Map.of("id", id);
  }

  @Transactional
  public Object category(long actor, Map<String, Object> b) {
    String name = s(b, "name").trim();
    Problem.require(!name.isBlank() && name.length() <= 60, "INVALID_INPUT", "分类名称无效");
    long id = n(b, "id");
    if (id == 0) {
      id = d.id();
      var category = new Category();
      category.setId(id);
      category.setName(name);
      category.setSortNo(Math.toIntExact(n(b, "sort_no")));
      categoryMapper.insert(category);
    } else {
      var category = com.sujian.mall.common.Rows.required(categoryMapper.selectById(id));
      category.setName(name);
      category.setSortNo(Math.toIntExact(n(b, "sort_no")));
      categoryMapper.updateById(category);
    }
    d.audit(actor, "CATEGORY_SAVE", "" + id, b);
    return Map.of("id", id);
  }
}
