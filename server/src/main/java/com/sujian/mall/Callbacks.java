package com.sujian.mall;

import static com.sujian.mall.Db.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/callbacks/wechat")
public class Callbacks {
  final Wechat w;
  final Commerce shop;
  final Jobs jobs;
  final Finance f;

  public Callbacks(Wechat w, Commerce s, Jobs j, Finance f) {
    this.w = w;
    shop = s;
    jobs = j;
    this.f = f;
  }

  @PostMapping("/{type}")
  public Map<String, String> notify(
      @PathVariable String type, @RequestBody String body, HttpServletRequest r) {
    w.verify(
        r.getHeader("Wechatpay-Serial"),
        r.getHeader("Wechatpay-Timestamp"),
        r.getHeader("Wechatpay-Nonce"),
        r.getHeader("Wechatpay-Signature"),
        body);
    var payload = w.decrypt(body);
    switch (type) {
      case "pay" -> shop.paymentEvidence(payload);
      case "refund" -> jobs.refundResult(s(payload, "out_refund_no"), payload);
      case "transfer" -> {
        String id = s(payload, "out_bill_no");
        jobs.validateTransfer(id, payload);
        f.transferResult(id, payload);
      }
      default -> throw new Problem(404, "NOT_FOUND", "回调不存在");
    }
    return Map.of("code", "SUCCESS", "message", "成功");
  }
}
