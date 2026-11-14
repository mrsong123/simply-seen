package com.sujian.mall.controller;

import com.sujian.mall.service.WechatCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/callbacks/wechat")
public class WechatCallbackController {
  private final WechatCallbackService callbacks;

  public WechatCallbackController(WechatCallbackService callbacks) {
    this.callbacks = callbacks;
  }

  @PostMapping("/{type}")
  public Map<String, String> notify(
      @PathVariable String type, @RequestBody String body, HttpServletRequest request) {
    return callbacks.handle(
        type,
        body,
        request.getHeader("Wechatpay-Serial"),
        request.getHeader("Wechatpay-Timestamp"),
        request.getHeader("Wechatpay-Nonce"),
        request.getHeader("Wechatpay-Signature"));
  }
}
