package com.sujian.mall.integration.wechat;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

@Component
public class WechatClient {
  private final Settings c;
  private final JsonCodec d;

  public WechatClient(Settings c, JsonCodec d) {
    this.c = c;
    this.d = d;
  }

  private HttpClient client() {
    var b = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
    String proxy = System.getenv("HTTPS_PROXY");
    if (proxy != null) {
      URI p = URI.create(proxy);
      b.proxy(ProxySelector.of(new InetSocketAddress(p.getHost(), p.getPort())));
    }
    return b.build();
  }

  public Map<String, Object> login(String code) {
    String uri =
        c.required("WX_LOGIN_URL")
            + "?appid="
            + enc(c.required("WX_APP_ID"))
            + "&secret="
            + enc(c.required("WX_APP_SECRET"))
            + "&js_code="
            + enc(code)
            + "&grant_type=authorization_code";
    return request("GET", uri, "", false);
  }

  private String enc(String x) {
    return URLEncoder.encode(x, StandardCharsets.UTF_8);
  }

  private PrivateKey privateKey() {
    try {
      String pem =
          Files.readString(Path.of(c.required("WX_PRIVATE_KEY_PATH")))
              .replaceAll("-----[^-]+-----|\\s", "");
      return KeyFactory.getInstance("RSA")
          .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    } catch (Exception e) {
      throw new Problem(503, "KEY_CONFIGURATION", "微信商户私钥不可用");
    }
  }

  private PublicKey publicKey() {
    try {
      String pem =
          Files.readString(Path.of(c.required("WX_PLATFORM_PUBLIC_KEY_PATH")))
              .replaceAll("-----[^-]+-----|\\s", "");
      return KeyFactory.getInstance("RSA")
          .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
    } catch (Exception e) {
      throw new Problem(503, "KEY_CONFIGURATION", "微信平台公钥不可用");
    }
  }

  String sign(String message) {
    try {
      Signature s = Signature.getInstance("SHA256withRSA");
      s.initSign(privateKey());
      s.update(message.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(s.sign());
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(503, "SIGN_FAILED", "请求签名失败");
    }
  }

  public void verify(String serial, String timestamp, String nonce, String signature, String body) {
    try {
      Problem.require(
          Objects.equals(serial, c.required("WX_PLATFORM_SERIAL")),
          "INVALID_SIGNATURE",
          "平台公钥编号不符");
      Problem.require(
          Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) <= 300,
          "INVALID_SIGNATURE",
          "回调时间失效");
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(publicKey());
      verifier.update(
          (timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
      Problem.require(
          verifier.verify(Base64.getDecoder().decode(signature)), "INVALID_SIGNATURE", "微信签名校验失败");
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "INVALID_SIGNATURE", "微信签名无效");
    }
  }

  public Map<String, Object> request(String method, String uri, String payload, boolean signed) {
    try {
      URI u = URI.create(uri);
      Problem.require(u.getScheme().equals("https"), "INSECURE_ENDPOINT", "外部接口必须使用HTTPS");
      var r =
          HttpRequest.newBuilder(u)
              .timeout(Duration.ofSeconds(20))
              .header("Accept", "application/json")
              .header("Content-Type", "application/json")
              .method(
                  method,
                  payload.isEmpty()
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(payload));
      if (signed) {
        String ts = "" + Instant.now().getEpochSecond(), nonce = UUID.randomUUID().toString();
        String target = u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        String signature =
            sign(method + "\n" + target + "\n" + ts + "\n" + nonce + "\n" + payload + "\n");
        r.header(
            "Authorization",
            "WECHATPAY2-SHA256-RSA2048 mchid=\""
                + c.required("WX_MCH_ID")
                + "\",nonce_str=\""
                + nonce
                + "\",timestamp=\""
                + ts
                + "\",serial_no=\""
                + c.required("WX_MERCHANT_SERIAL")
                + "\",signature=\""
                + signature
                + "\"");
      }
      var response = client().send(r.build(), HttpResponse.BodyHandlers.ofString());
      String body = response.body();
      if (signed)
        verify(
            response.headers().firstValue("Wechatpay-Serial").orElse(""),
            response.headers().firstValue("Wechatpay-Timestamp").orElse(""),
            response.headers().firstValue("Wechatpay-Nonce").orElse(""),
            response.headers().firstValue("Wechatpay-Signature").orElse(""),
            body);
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw channelError(body);
      return body.isBlank() ? Map.of() : d.decode(body);
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(503, "CHANNEL_UNKNOWN", "渠道结果待确认，请勿重复提交");
    }
  }

  private Problem channelError(String body) {
    String code = "CHANNEL_REJECTED";
    try {
      String external = s(d.decode(body), "code");
      if (external.matches("[A-Z0-9_]{1,64}")) code = "WX_" + external;
    } catch (Exception ignored) {
    }
    return new Problem(502, code, "微信渠道拒绝请求（" + code + "），请核对商户配置及业务状态");
  }

  public Map<String, Object> prepay(Map<String, Object> o, String openid) {
    String no = s(o, "id");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("appid", c.required("WX_APP_ID"));
    body.put("mchid", c.required("WX_MCH_ID"));
    body.put("description", "素见好物订单 " + no);
    body.put("out_trade_no", no);
    body.put("notify_url", c.required("WX_PAY_NOTIFY_URL"));
    body.put("amount", Map.of("total", n(o, "cash_due"), "currency", "CNY"));
    body.put("payer", Map.of("openid", openid));
    var result =
        request(
            "POST",
            c.required("WX_PAY_BASE_URL") + "/v3/pay/transactions/jsapi",
            d.encode(body),
            true);
    String ts = "" + Instant.now().getEpochSecond(),
        nonce = UUID.randomUUID().toString(),
        pkg = "prepay_id=" + s(result, "prepay_id");
    return Map.of(
        "timeStamp",
        ts,
        "nonceStr",
        nonce,
        "package",
        pkg,
        "signType",
        "RSA",
        "paySign",
        sign(c.required("WX_APP_ID") + "\n" + ts + "\n" + nonce + "\n" + pkg + "\n"));
  }

  public Map<String, Object> queryPayment(String no) {
    return request(
        "GET",
        c.required("WX_PAY_BASE_URL")
            + "/v3/pay/transactions/out-trade-no/"
            + enc(no)
            + "?mchid="
            + enc(c.required("WX_MCH_ID")),
        "",
        true);
  }

  public void close(String no) {
    request(
        "POST",
        c.required("WX_PAY_BASE_URL") + "/v3/pay/transactions/out-trade-no/" + enc(no) + "/close",
        d.encode(Map.of("mchid", c.required("WX_MCH_ID"))),
        true);
  }

  public Map<String, Object> refund(Map<String, Object> r, Map<String, Object> o) {
    return request(
        "POST",
        c.required("WX_PAY_BASE_URL") + "/v3/refund/domestic/refunds",
        d.encode(
            Map.of(
                "out_trade_no",
                s(o, "id"),
                "out_refund_no",
                s(r, "id"),
                "reason",
                s(r, "reason"),
                "notify_url",
                c.required("WX_REFUND_NOTIFY_URL"),
                "amount",
                Map.of("refund", n(r, "amount"), "total", n(o, "cash_due"), "currency", "CNY"))),
        true);
  }

  public Map<String, Object> queryRefund(String no) {
    return request(
        "GET", c.required("WX_PAY_BASE_URL") + "/v3/refund/domestic/refunds/" + enc(no), "", true);
  }

  public Map<String, Object> transfer(Map<String, Object> r, String openid) {
    var body = new LinkedHashMap<String, Object>();
    body.put("appid", c.required("WX_APP_ID"));
    body.put("out_bill_no", s(r, "id"));
    body.put("transfer_scene_id", c.required("WX_TRANSFER_SCENE_ID"));
    body.put("openid", openid);
    body.put("transfer_amount", n(r, "net"));
    body.put("transfer_remark", "商品推广佣金结算");
    body.put("notify_url", c.required("WX_TRANSFER_NOTIFY_URL"));
    body.put("user_recv_perception", c.required("WX_TRANSFER_PERCEPTION"));
    body.put("transfer_scene_report_infos", d.list(c.required("WX_TRANSFER_REPORT_INFOS")));
    return request(
        "POST",
        c.required("WX_TRANSFER_BASE_URL") + "/v3/fund-app/mch-transfer/transfer-bills",
        d.encode(body),
        true);
  }

  public Map<String, Object> queryTransfer(String no) {
    return request(
        "GET",
        c.required("WX_TRANSFER_BASE_URL")
            + "/v3/fund-app/mch-transfer/transfer-bills/out-bill-no/"
            + enc(no),
        "",
        true);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> decrypt(String body) {
    try {
      var resource = (Map<String, Object>) d.decode(body).get("resource");
      Problem.require(
          s(resource, "algorithm").equals("AEAD_AES_256_GCM"), "INVALID_CALLBACK", "不支持的回调加密算法");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(c.required("WX_API_V3_KEY").getBytes(StandardCharsets.UTF_8), "AES"),
          new GCMParameterSpec(128, s(resource, "nonce").getBytes(StandardCharsets.UTF_8)));
      cipher.updateAAD(s(resource, "associated_data").getBytes(StandardCharsets.UTF_8));
      return d.decode(
          new String(
              cipher.doFinal(Base64.getDecoder().decode(s(resource, "ciphertext"))),
              StandardCharsets.UTF_8));
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "INVALID_CALLBACK", "微信回调解密失败");
    }
  }
}
