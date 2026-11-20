package com.sujian.mall.integration.wechat;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WechatClient {
  private final Settings c;
  private final JsonCodec d;
  private final WechatCryptography cryptography;

  public WechatClient(Settings c, JsonCodec d) {
    this.c = c;
    this.d = d;
    cryptography = new WechatCryptography(c, d);
  }

  protected HttpClient client() {
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

  String sign(String message) { return cryptography.sign(message); }

  public void verify(String serial, String timestamp, String nonce, String signature, String body) {
    cryptography.verify(serial, timestamp, nonce, signature, body);
  }

  public Map<String, Object> request(String method, String uri, String payload, boolean signed) {
    return request(method, uri, payload, signed, Map.of());
  }

  protected Map<String, Object> request(String method, String uri, String payload, boolean signed,
      Map<String, String> additionalHeaders) {
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
      additionalHeaders.forEach(r::header);
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

  public Map<String, Object> prepay(Map<String, Object> order, String openid) {
    return prepay(order, openid, "MINI_PROGRAM", "");
  }

  public Map<String, Object> prepay(
      Map<String, Object> order, String openid, String channel, String clientIp) {
    return WechatPrepayment.create(this, c, d, order, openid, channel, clientIp);
  }

  public Map<String, Object> refreshMiniPrepay(Map<String, Object> stored) {
    return WechatPrepayment.miniSignature(this, c, s(stored, "package"));
  }

  public Map<String, Object> refreshMiniPrepay(Map<String, Object> stored, String appId) {
    return WechatPrepayment.miniSignature(this, s(stored, "package"), appId);
  }

  public Map<String, Object> queryPayment(String no) {
    var result = request(
        "GET",
        c.required("WX_PAY_BASE_URL")
            + "/v3/pay/transactions/out-trade-no/"
            + enc(no)
            + "?mchid="
            + enc(c.required("WX_MCH_ID")),
        "",
        true);
    Problem.require(no.equals(s(result, "out_trade_no")), "PAYMENT_ORDER_MISMATCH", "渠道订单编号不符");
    return result;
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
    var result = request(
        "GET", c.required("WX_PAY_BASE_URL") + "/v3/refund/domestic/refunds/" + enc(no), "", true);
    Problem.require(no.equals(s(result, "out_refund_no")), "REFUND_ORDER_MISMATCH", "渠道退款单号不符");
    return result;
  }

  public Map<String, Object> transfer(Map<String, Object> r, String openid) {
    return transfer(r, openid, "");
  }

  public Map<String, Object> transfer(Map<String, Object> r, String openid, String verifiedName) {
    Problem.require(!openid.isBlank() && !openid.startsWith("dev:"),
        "WECHAT_BINDING_REQUIRED", "真实转账需要已绑定的微信账户");
    boolean hasName = verifiedName != null && !verifiedName.isBlank();
    Problem.require(n(r, "net") < 200000 || hasName,
        "TRANSFER_NAME_REQUIRED", "单笔转账达到2000元时必须提供已核验收款姓名");
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
    if (hasName) body.put("user_name", cryptography.encryptName(verifiedName));
    return request(
        "POST",
        c.required("WX_TRANSFER_BASE_URL") + "/v3/fund-app/mch-transfer/transfer-bills",
        d.encode(body),
        true,
        hasName ? Map.of("Wechatpay-Serial", c.required("WX_PLATFORM_SERIAL")) : Map.of());
  }

  public Map<String, Object> queryTransfer(String no) {
    var result = request(
        "GET",
        c.required("WX_TRANSFER_BASE_URL")
            + "/v3/fund-app/mch-transfer/transfer-bills/out-bill-no/"
            + enc(no),
        "",
        true);
    Problem.require(no.equals(s(result, "out_bill_no")), "TRANSFER_ORDER_MISMATCH", "渠道转账单号不符");
    return result;
  }

  public Map<String, Object> decrypt(String body) { return cryptography.decrypt(body); }
}
