package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AddressController {
  private final AuthService auth;
  private final CommerceService shop;
  private final AdminService admin;

  public AddressController(AuthService auth, CommerceService shop, AdminService admin) {
    this.auth = auth;
    this.shop = shop;
    this.admin = admin;
  }

  @GetMapping("/addresses")
  public Object addresses(HttpServletRequest request) {
    return shop.addresses(auth.member(request).id());
  }

  @PostMapping("/addresses")
  public Object saveAddress(@Valid @RequestBody AddressRequest body, HttpServletRequest request) {
    return shop.address(auth.member(request).id(), body.toMap());
  }

  @DeleteMapping("/addresses/{id}")
  public void deleteAddress(@PathVariable long id, HttpServletRequest request) {
    admin.deleteAddress(id, auth.member(request).id());
  }
}
