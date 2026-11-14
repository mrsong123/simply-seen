package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartServiceImpl implements CartService {
  @org.springframework.beans.factory.annotation.Autowired private CartItemMapper cartItemMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private SkuMapper skuMapper;

  private final BusinessSupport d;

  public CartServiceImpl(BusinessSupport d) {
    this.d = d;
  }

  public List<Map<String, Object>> cart(long uid) {
    return cartItemMapper.listForCart(uid);
  }

  @Transactional
  public void cart(long uid, long sku, int qty) {
    Problem.require(qty >= 0 && qty <= 99, "INVALID_QUANTITY", "每种规格最多99件");
    com.sujian.mall.common.Rows.required(memberMapper.lockMemberId(uid));
    cartItemMapper.deleteForCart(uid, sku);
    if (qty > 0) {
      com.sujian.mall.common.Rows.required(skuMapper.selectOnSaleSkuId(sku));
      cartItemMapper.insertForCart(uid, sku, qty);
    }
  }
}
