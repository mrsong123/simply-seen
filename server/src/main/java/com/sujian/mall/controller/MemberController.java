package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class MemberController {
  private final AuthService auth;
  private final AdminService admin;
  private final FinanceService finance;

  public MemberController(AuthService auth, AdminService admin, FinanceService finance) {
    this.auth = auth;
    this.admin = admin;
    this.finance = finance;
  }

  @GetMapping("/me")
  public Object profile(HttpServletRequest request) {
    return auth.profile(auth.member(request).id());
  }

  @PutMapping("/me")
  public void updateProfile(
      @Valid @RequestBody AuthRequests.Profile body, HttpServletRequest request) {
    auth.updateProfile(auth.member(request).id(), body.toMap());
  }

  @PostMapping("/me/promoter")
  public void acceptPromotion(
      @Valid @RequestBody AuthRequests.Acceptance body, HttpServletRequest request) {
    admin.acceptPromoter(auth.member(request).id(), body.toMap());
  }

  @GetMapping("/me/ledger")
  public Object ledger(HttpServletRequest request) {
    return admin.ledger(auth.member(request).id());
  }

  @GetMapping("/me/team")
  public Object team(HttpServletRequest request) {
    return MemberCommerceView.team(admin.team(auth.member(request).id()));
  }

  @GetMapping("/me/withdrawals")
  public Object withdrawals(HttpServletRequest request) {
    return admin.withdrawals(auth.member(request).id());
  }

  @PostMapping("/me/withdrawals")
  public Object withdraw(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody TradeRequests.Withdrawal body,
      HttpServletRequest request) {
    return finance.withdraw(auth.member(request).id(), key, body.toMap());
  }

  @GetMapping("/me/awards")
  public Object awards(HttpServletRequest request) {
    return admin.awards(auth.member(request).id());
  }
}
