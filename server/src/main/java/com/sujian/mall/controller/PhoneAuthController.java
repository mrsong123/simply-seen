package com.sujian.mall.controller;

import com.sujian.mall.dto.PhoneRequests;
import com.sujian.mall.service.PhoneAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/phone")
public class PhoneAuthController {
  private final PhoneAuthService phones;

  public PhoneAuthController(PhoneAuthService phones) { this.phones = phones; }

  @PostMapping("/code")
  public Object code(@Valid @RequestBody PhoneRequests.SendCode body, HttpServletRequest request) {
    return phones.sendLoginCode(body.phone(), request.getRemoteAddr());
  }

  @PostMapping
  public Object login(@Valid @RequestBody PhoneRequests.Login body, HttpServletRequest request) {
    return phones.login(body.phone(), body.code(), body.challengeId(), body.inviteCode(),
        Boolean.TRUE.equals(body.accepted()), request.getRemoteAddr());
  }
}
