package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdminCatalogController {
  private final AuthService auth;
  private final AdminService admin;

  public AdminCatalogController(AuthService auth, AdminService admin) {
    this.auth = auth;
    this.admin = admin;
  }

  @GetMapping("/admin/products")
  public Object products(HttpServletRequest request) {
    auth.admin(request, "OPERATOR");
    return admin.adminProducts();
  }

  @PostMapping("/admin/products")
  public Object saveProduct(
      @Valid @RequestBody AdminRequests.Product body, HttpServletRequest request) {
    return admin.saveProduct(auth.admin(request, "OPERATOR").id(), body.toMap());
  }

  @PostMapping("/admin/categories")
  public Object saveCategory(
      @Valid @RequestBody AdminRequests.Category body, HttpServletRequest request) {
    return admin.category(auth.admin(request, "OPERATOR").id(), body.toMap());
  }
}
