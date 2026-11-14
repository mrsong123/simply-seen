package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
  private final AuthService auth;
  private final CommerceService shop;
  private final AdminService admin;
  private final LogisticsService logistics;

  public OrderController(
      AuthService auth, CommerceService shop, AdminService admin, LogisticsService logistics) {
    this.auth = auth;
    this.shop = shop;
    this.admin = admin;
    this.logistics = logistics;
  }

  @PostMapping("/checkout/quote")
  public Object quote(@Valid @RequestBody TradeRequests.Quote body, HttpServletRequest request) {
    return shop.quote(auth.member(request).id(), body.toMap());
  }

  @PostMapping("/orders")
  public Object create(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody TradeRequests.CreateOrder body,
      HttpServletRequest request) {
    return shop.create(auth.member(request).id(), key, body.toMap());
  }

  @GetMapping("/orders")
  public Object orders(HttpServletRequest request) {
    return shop.orders(auth.member(request).id());
  }

  @GetMapping("/orders/{no}")
  public Object order(@PathVariable String no, HttpServletRequest request) {
    return shop.order(auth.member(request).id(), no);
  }

  @PostMapping("/orders/{no}/prepay")
  public Object prepay(@PathVariable String no, HttpServletRequest request) {
    return shop.prepay(auth.member(request).id(), no);
  }

  @PostMapping("/orders/{no}/cancel")
  public void cancel(@PathVariable String no, HttpServletRequest request) {
    shop.cancel(auth.member(request).id(), no);
  }

  @GetMapping("/orders/{no}/payment-status")
  public Object paymentStatus(@PathVariable String no, HttpServletRequest request) {
    return admin.payStatus(auth.member(request).id(), no);
  }

  @GetMapping("/orders/{no}/logistics")
  public Object logistics(@PathVariable String no, HttpServletRequest request) {
    return logistics.query(auth.member(request).id(), no);
  }

  @PostMapping("/orders/{no}/receive")
  public void receive(@PathVariable String no, HttpServletRequest request) {
    shop.receive(auth.member(request).id(), no);
  }

  @PostMapping("/dev/orders/{no}/pay")
  public void developmentPay(@PathVariable String no, HttpServletRequest request) {
    admin.devPay(auth.member(request).id(), no);
  }
}
