package com.sujian.mall.controller;

import com.sujian.mall.dto.PhoneRequests;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class AccountBindingController {
  private final AuthService auth;
  private final PhoneAuthService phones;

  public AccountBindingController(AuthService auth, PhoneAuthService phones) {
    this.auth = auth;
    this.phones = phones;
  }

  @PostMapping("/phone/code")
  public Object code(@Valid @RequestBody PhoneRequests.SendCode body, HttpServletRequest request) {
    return phones.sendBindingCode(auth.member(request).id(), body.phone(), request.getRemoteAddr());
  }

  @PostMapping("/phone")
  public Object bindPhone(@Valid @RequestBody PhoneRequests.BindPhone body, HttpServletRequest request) {
    return phones.bindPhone(auth.member(request).id(), body.phone(), body.code(), body.challengeId(),
        request.getRemoteAddr());
  }

  @PostMapping("/wechat")
  public Object bindWechat(@Valid @RequestBody PhoneRequests.BindWechat body, HttpServletRequest request) {
    return phones.bindWechat(auth.member(request).id(), body.code(), request.getRemoteAddr());
  }
}
