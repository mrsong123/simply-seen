package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sujian.mall.common.*;
import com.sujian.mall.entity.Product;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CatalogServiceImpl implements CatalogService {
  @org.springframework.beans.factory.annotation.Autowired private ProductMapper productMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private RuleVersionMapper ruleVersionMapper;

  @org.springframework.beans.factory.annotation.Autowired private SkuMapper skuMapper;

  private final BusinessSupport d;

  public CatalogServiceImpl(BusinessSupport d) {
    this.d = d;
  }

  public Map<String, Object> rules() {
    var rows = ruleVersionMapper.listForRules();
    Problem.require(!rows.isEmpty(), "RULE_REQUIRED", "请先发布交易规则");
    var rule = d.decode(s(rows.getFirst(), "config"));
    rule.put("version", n(rows.getFirst(), "id"));
    return rule;
  }

  public List<Map<String, Object>> products(String category, String search) {
    Long categoryId = category.isBlank() ? null : Long.parseLong(category);
    String pattern =
        search.isBlank() ? null : "%" + search.substring(0, Math.min(80, search.length())) + "%";
    return productMapper.search(categoryId, pattern);
  }

  public Map<String, Object> product(long id) {
    var entity =
        com.sujian.mall.common.Rows.required(
            productMapper.selectOne(
                Wrappers.<Product>lambdaQuery()
                    .eq(Product::getId, id)
                    .eq(Product::getStatus, "ON_SALE")));
    var p = d.decode(d.encode(entity));
    p.put("skus", skuMapper.selectPublicSkus(id));
    return p;
  }
}
