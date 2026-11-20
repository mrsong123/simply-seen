package com.sujian.mall.controller;

import com.sujian.mall.dto.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RecordPageController {
  private final AuthService auth;
  private final RecordPageService records;
  public RecordPageController(AuthService auth, RecordPageService records) {
    this.auth=auth; this.records=records;
  }

  @GetMapping("/products/page")
  public RecordPage products(@Valid @ModelAttribute RecordPageQuery query) {
    return records.products(query);
  }

  @GetMapping("/admin/records/{resource}")
  public RecordPage admin(@PathVariable String resource, @Valid @ModelAttribute RecordPageQuery query,
      HttpServletRequest request) {
    return records.admin(auth.admin(request,"OPERATOR","FINANCE"),resource,query);
  }

  @GetMapping("/me/records/{resource}")
  public RecordPage member(@PathVariable String resource, @Valid @ModelAttribute RecordPageQuery query,
      HttpServletRequest request) {
    return records.member(auth.member(request).id(),resource,query);
  }
}
