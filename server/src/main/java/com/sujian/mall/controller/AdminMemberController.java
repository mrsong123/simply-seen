package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdminMemberController {
  private final AuthService auth;
  private final AdminService admin;

  public AdminMemberController(AuthService auth, AdminService admin) {
    this.auth = auth;
    this.admin = admin;
  }

  @GetMapping("/admin/members")
  public Object members(HttpServletRequest request) {
    auth.admin(request, "OPERATOR", "FINANCE");
    return admin.members();
  }

  @PostMapping("/admin/members/{id}/block")
  public void block(
      @PathVariable long id,
      @Valid @RequestBody AuthRequests.Blocked body,
      HttpServletRequest request) {
    admin.block(auth.admin(request).id(), id, body.toMap());
  }
}
