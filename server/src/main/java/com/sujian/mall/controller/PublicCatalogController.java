package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PublicCatalogController {
  private final AdminService admin;
  private final CommerceService shop;
  private final com.sujian.mall.config.Settings settings;

  public PublicCatalogController(
      AdminService admin, CommerceService shop, com.sujian.mall.config.Settings settings) {
    this.admin = admin;
    this.shop = shop;
    this.settings = settings;
  }

  @GetMapping("/config")
  public Object config() {
    return settings.publicConfig();
  }

  @GetMapping("/categories")
  public Object categories() {
    return admin.categories();
  }

  @GetMapping("/products")
  public Object products(
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "") String search) {
    return shop.products(category, search);
  }

  @GetMapping("/products/{id}")
  public Object product(@PathVariable long id) {
    return shop.product(id);
  }

  @GetMapping("/rules/public")
  public Object rules() {
    return admin.rules();
  }
}
