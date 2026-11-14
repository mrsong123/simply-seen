package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdminOrderController {
  private final AuthService auth;
  private final CommerceService shop;
  private final AdminService admin;

  public AdminOrderController(AuthService auth, CommerceService shop, AdminService admin) {
    this.auth = auth;
    this.shop = shop;
    this.admin = admin;
  }

  @GetMapping("/admin/summary")
  public Object summary(HttpServletRequest request) {
    auth.admin(request, "OPERATOR", "FINANCE");
    return admin.summary();
  }

  @GetMapping("/admin/orders")
  public Object orders(HttpServletRequest request) {
    auth.admin(request, "OPERATOR", "FINANCE");
    return admin.adminOrders();
  }

  @GetMapping("/admin/orders/{no}")
  public Object order(@PathVariable String no, HttpServletRequest request) {
    auth.admin(request, "OPERATOR", "FINANCE");
    return admin.adminOrder(no);
  }

  @PostMapping("/admin/orders/{no}/ship")
  public void ship(
      @PathVariable String no,
      @Valid @RequestBody TradeRequests.Shipment body,
      HttpServletRequest request) {
    shop.ship(auth.admin(request, "OPERATOR").id(), no, body.toMap());
  }

  @GetMapping("/admin/refunds")
  public Object refunds(HttpServletRequest request) {
    auth.admin(request, "OPERATOR", "FINANCE");
    return admin.refunds();
  }

  @PostMapping("/admin/refunds/{id}/review")
  public void reviewRefund(
      @PathVariable String id,
      @Valid @RequestBody AdminRequests.Review body,
      HttpServletRequest request) {
    admin.reviewRefund(auth.admin(request, "FINANCE").id(), id, body.toMap());
  }
}
