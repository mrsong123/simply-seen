package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdminMarketingController {
  private final AuthService auth;
  private final AdminService admin;

  public AdminMarketingController(AuthService auth, AdminService admin) {
    this.auth = auth;
    this.admin = admin;
  }

  @GetMapping("/admin/rules")
  public Object rules(HttpServletRequest request) {
    auth.admin(request, "OPERATOR", "FINANCE");
    return admin.adminRules();
  }

  @PostMapping("/admin/rules")
  public void draftRule(@Valid @RequestBody AdminRequests.Rule body, HttpServletRequest request) {
    admin.saveRule(auth.admin(request, "OPERATOR").id(), body.toMap());
  }

  @PostMapping("/admin/rules/{id}/approve")
  public void approveRule(@PathVariable long id, HttpServletRequest request) {
    admin.approveRule(auth.admin(request).id(), id);
  }

  @GetMapping("/admin/settlements")
  public Object settlements(HttpServletRequest request) {
    auth.admin(request, "OPERATOR", "FINANCE");
    return admin.settlements();
  }

  @PostMapping("/admin/settlements")
  public Object draftSettlement(
      @Valid @RequestBody AdminRequests.Month body, HttpServletRequest request) {
    return admin.settlement(auth.admin(request, "OPERATOR").id(), body.toMap());
  }

  @PostMapping("/admin/settlements/{id}/approve")
  public void approveSettlement(@PathVariable long id, HttpServletRequest request) {
    admin.approveSettlement(auth.admin(request, "FINANCE").id(), id);
  }
}
