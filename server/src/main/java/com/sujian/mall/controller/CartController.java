package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CartController {
  private final AuthService auth;
  private final CommerceService shop;

  public CartController(AuthService auth, CommerceService shop) {
    this.auth = auth;
    this.shop = shop;
  }

  @GetMapping("/cart")
  public Object cart(HttpServletRequest request) {
    return shop.cart(auth.member(request).id());
  }

  @PutMapping("/cart/{sku}")
  public void quantity(
      @PathVariable long sku,
      @Valid @RequestBody TradeRequests.Quantity body,
      HttpServletRequest request) {
    shop.cart(auth.member(request).id(), sku, body.quantity());
  }
}
