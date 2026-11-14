package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdminFinanceController {
  private final AuthService auth;
  private final AdminService admin;

  public AdminFinanceController(AuthService auth, AdminService admin) {
    this.auth = auth;
    this.admin = admin;
  }

  @GetMapping("/admin/commissions")
  public Object commissions(HttpServletRequest request) {
    auth.admin(request, "FINANCE");
    return admin.commissions();
  }

  @GetMapping("/admin/ledger")
  public Object ledger(HttpServletRequest request) {
    auth.admin(request, "FINANCE");
    return admin.adminLedger();
  }

  @GetMapping("/admin/withdrawals")
  public Object withdrawals(HttpServletRequest request) {
    auth.admin(request, "FINANCE");
    return admin.adminWithdrawals();
  }

  @PostMapping("/admin/withdrawals/{id}/review")
  public void review(
      @PathVariable String id,
      @Valid @RequestBody AdminRequests.Review body,
      HttpServletRequest request) {
    admin.reviewWithdraw(auth.admin(request, "FINANCE").id(), id, body.toMap());
  }

  @PostMapping("/admin/withdrawals/{id}/execute")
  public void execute(@PathVariable String id, HttpServletRequest request) {
    admin.execute(auth.admin(request).id(), id);
  }

  @PostMapping("/admin/withdrawals/{id}/query")
  public void query(@PathVariable String id, HttpServletRequest request) {
    auth.admin(request, "FINANCE");
    admin.queryTransfer(id);
  }

  @PostMapping("/admin/dev/withdrawals/{id}/confirm")
  public void developmentConfirm(@PathVariable String id, HttpServletRequest request) {
    admin.devTransfer(auth.admin(request).id(), id);
  }

  @GetMapping("/admin/reconciliation")
  public Object reconciliation(HttpServletRequest request) {
    auth.admin(request, "FINANCE");
    return admin.reconciliation();
  }

  @PostMapping("/admin/reconciliation")
  public Object reconcile(HttpServletRequest request) {
    return admin.reconcile(auth.admin(request, "FINANCE").id());
  }

  @PostMapping("/admin/issues/{id}/resolve")
  public void resolve(
      @PathVariable long id,
      @Valid @RequestBody AdminRequests.Reason body,
      HttpServletRequest request) {
    admin.resolve(auth.admin(request, "FINANCE").id(), id, body.toMap());
  }
}
