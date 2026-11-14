package com.sujian.mall.controller;

import com.sujian.mall.dto.IdentityRequests;
import com.sujian.mall.service.AuthService;
import com.sujian.mall.service.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/identity")
public class IdentityController {
  private final AuthService auth;
  private final IdentityService identities;

  public IdentityController(AuthService auth, IdentityService identities) {
    this.auth = auth;
    this.identities = identities;
  }

  @GetMapping
  public Object status(HttpServletRequest request) {
    return identities.status(auth.member(request).id());
  }

  @PostMapping
  public Object verify(
      @Valid @RequestBody IdentityRequests.Verification body, HttpServletRequest request) {
    long memberId = auth.member(request).id();
    auth.limit("identity-ip:" + com.sujian.mall.common.BusinessSupport.hash(request.getRemoteAddr()), 10);
    return identities.verify(memberId, body.name(), body.idNumber(), body.accepted());
  }
}
