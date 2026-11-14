package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdminAccountController {
  private final AuthService auth;
  private final AdminService admin;

  public AdminAccountController(AuthService auth, AdminService admin) {
    this.auth = auth;
    this.admin = admin;
  }

  @GetMapping("/admin/me")
  public Object me(HttpServletRequest request) {
    return admin.adminMe(auth.admin(request, "OPERATOR", "FINANCE").id());
  }

  @PostMapping("/admin/password")
  public void password(@Valid @RequestBody AuthRequests.Password body, HttpServletRequest request) {
    auth.password(auth.admin(request, "OPERATOR", "FINANCE").id(), body.toMap());
  }

  @GetMapping("/admin/accounts")
  public Object accounts(HttpServletRequest request) {
    auth.admin(request);
    return admin.accounts();
  }

  @PostMapping("/admin/accounts")
  public void create(@Valid @RequestBody AuthRequests.Account body, HttpServletRequest request) {
    admin.createAccount(auth.admin(request).id(), body.toMap());
  }

  @PostMapping("/admin/accounts/{id}/active")
  public void active(
      @PathVariable long id,
      @Valid @RequestBody AuthRequests.Active body,
      HttpServletRequest request) {
    admin.accountActive(auth.admin(request).id(), id, body.toMap());
  }

  @PostMapping("/admin/accounts/{id}/reset-password")
  public void resetPassword(
      @PathVariable long id,
      @Valid @RequestBody AuthRequests.ResetPassword body,
      HttpServletRequest request) {
    admin.resetPassword(auth.admin(request).id(), id, body.toMap());
  }

  @GetMapping("/admin/audit")
  public Object audit(HttpServletRequest request) {
    auth.admin(request, "FINANCE");
    return admin.audit();
  }
}
