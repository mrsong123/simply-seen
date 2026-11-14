package com.sujian.mall.integration.aliyun;

import com.sujian.mall.common.Problem;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Alibaba Cloud RPC SignatureVersion 1.0: RFC3986 encoding and HMAC-SHA1. */
public final class AliyunRpcSigner {
  private AliyunRpcSigner() {}

  public static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~");
  }

  public static String form(Map<String, String> parameters) {
    return new TreeMap<>(parameters).entrySet().stream()
        .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
        .collect(Collectors.joining("&"));
  }

  public static String sign(Map<String, String> parameters, String accessKeySecret) {
    var unsigned = new TreeMap<>(parameters);
    unsigned.remove("Signature");
    String stringToSign = "POST&%2F&" + encode(form(unsigned));
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(
          new SecretKeySpec(
              (accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
      return Base64.getEncoder()
          .encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      throw new Problem(503, "ACCOUNT_PROVIDER_SIGNATURE", "账户验证服务签名配置不可用");
    }
  }
}
