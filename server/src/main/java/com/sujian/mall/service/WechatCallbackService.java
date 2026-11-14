package com.sujian.mall.service;

import java.util.Map;

public interface WechatCallbackService {
  Map<String, String> handle(
      String type, String body, String serial, String timestamp, String nonce, String signature);
}
