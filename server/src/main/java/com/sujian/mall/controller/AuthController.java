package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/auth/wechat")
  public Object login(
      @Valid @RequestBody AuthRequests.WechatLogin body, HttpServletRequest request) {
    return auth.login(body.toMap(), request.getRemoteAddr());
  }

  @PostMapping("/auth/dev")
  public Object developmentLogin(
      @Valid @RequestBody AuthRequests.DevelopmentLogin body, HttpServletRequest request) {
    auth.limit("dev:" + request.getRemoteAddr(), 30);
    return auth.devLogin(body.toMap());
  }

  @PostMapping("/auth/admin")
  public Object adminLogin(
      @Valid @RequestBody AuthRequests.AdminLogin body, HttpServletRequest request) {
    return auth.adminLogin(body.toMap(), request.getRemoteAddr());
  }

  @PostMapping("/auth/logout")
  public void logout(HttpServletRequest request) {
    auth.logout(request);
  }
}
