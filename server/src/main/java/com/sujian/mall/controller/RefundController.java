package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RefundController {
  private final AuthService auth;
  private final FinanceService finance;

  public RefundController(AuthService auth, FinanceService finance) {
    this.auth = auth;
    this.finance = finance;
  }

  @PostMapping("/refunds")
  public Object requestRefund(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody TradeRequests.Refund body,
      HttpServletRequest request) {
    return finance.refund(auth.member(request).id(), key, body.toMap());
  }
}
