package com.sujian.mall.integration.wechat;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native, H5 and mini-program request/response contracts share one merchant order. */
final class WechatPrepayment {
  private WechatPrepayment() {}

  static Map<String, Object> create(WechatClient api, Settings settings, JsonCodec json,
      Map<String, Object> order, String openid, String channel, String clientIp) {
    String no = s(order, "id");
    long expiry = n(order, "expires_at");
    Problem.require(expiry > System.currentTimeMillis() + 60000,
        "ORDER_EXPIRING", "订单即将过期，请取消后重新下单");
    var body = new LinkedHashMap<String, Object>();
    PaymentChannel selected;
    try { selected = PaymentChannel.valueOf(channel); }
    catch (IllegalArgumentException failure) { throw new Problem(400, "PAYMENT_CHANNEL", "支付方式不受支持"); }
    String appId = s(order, "payment_app_id");
    if (appId.isBlank()) appId = selected.appId(settings);
    body.put("appid", appId);
    body.put("mchid", settings.required("WX_MCH_ID"));
    body.put("description", "素见好物订单 " + no);
    body.put("out_trade_no", no);
    body.put("notify_url", settings.required("WX_PAY_NOTIFY_URL"));
    body.put("time_expire", Instant.ofEpochMilli(expiry).atOffset(ZoneOffset.ofHours(8))
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    body.put("amount", Map.of("total", n(order, "cash_due"), "currency", "CNY"));
    String product = selected.product();
    if (channel.equals("MINI_PROGRAM")) body.put("payer", Map.of("openid", openid));
    if (channel.equals("H5")) {
      Problem.require(validIp(clientIp), "CLIENT_IP_REQUIRED", "无法确认支付客户端地址，请稍后重试");
      String name = settings.get("WX_H5_APP_NAME", "").trim();
      body.put("scene_info", Map.of("payer_client_ip", clientIp, "h5_info", Map.of(
          "type", "Wap",
          "app_name", name.isBlank() ? "素见好物" : name,
          "app_url", settings.required("PUBLIC_STORE_URL"))));
    }
    var result = api.request("POST", settings.required("WX_PAY_BASE_URL")
        + "/v3/pay/transactions/" + product, json.encode(body), true);
    if (channel.equals("NATIVE")) {
      String code = s(result, "code_url");
      Problem.require(code.length() <= 4096 && code.startsWith("weixin://wxpay/bizpayurl?"),
          "INVALID_PREPAY_RESPONSE", "微信未返回有效支付二维码，请查询订单后重试");
      return Map.of("mode", "NATIVE", "orderNo", no, "codeUrl", code, "expiresAt", expiry);
    }
    if (channel.equals("H5")) {
      String url = h5Url(settings, s(result, "h5_url"), no);
      return Map.of("mode", "H5", "orderNo", no, "h5Url", url, "expiresAt", expiry);
    }
    String prepay = s(result, "prepay_id");
    Problem.require(prepay.matches("[A-Za-z0-9_-]{1,160}"),
        "INVALID_PREPAY_RESPONSE", "微信未返回有效预支付凭据，请查询订单后重试");
    return miniSignature(api, "prepay_id=" + prepay, appId);
  }

  static Map<String, Object> miniSignature(WechatClient api, Settings settings, String pkg) {
    return miniSignature(api, pkg, settings.required("WX_APP_ID"));
  }

  static Map<String, Object> miniSignature(WechatClient api, String pkg, String appId) {
    Problem.require(pkg.matches("prepay_id=[A-Za-z0-9_-]{1,160}"),
        "INVALID_PREPAY_RESPONSE", "预支付凭据无效，请重新查询订单");
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    String nonce = UUID.randomUUID().toString();
    return Map.of("timeStamp", timestamp, "nonceStr", nonce, "package", pkg, "signType", "RSA",
        "paySign", api.sign(appId + "\n" + timestamp + "\n" + nonce
            + "\n" + pkg + "\n"));
  }

  private static boolean validIp(String value) {
    if (value == null || !value.matches("[0-9a-fA-F:.]{2,64}")) return false;
    try { InetAddress.getByName(value); return true; }
    catch (UnknownHostException error) { return false; }
  }

  private static String h5Url(Settings settings, String value, String no) {
    try {
      URI url = URI.create(value), store = URI.create(settings.required("PUBLIC_STORE_URL"));
      var hosts = Arrays.stream(settings.get("WX_H5_ALLOWED_HOSTS", "wx.tenpay.com").split(","))
          .map(String::trim).map(x -> x.toLowerCase(Locale.ROOT)).toList();
      Problem.require("https".equals(url.getScheme()) && url.getHost() != null
              && hosts.contains(url.getHost().toLowerCase(Locale.ROOT))
              && (url.getPort() == -1 || url.getPort() == 443) && value.length() <= 4096
              && url.getUserInfo() == null && url.getFragment() == null
              && (url.getRawQuery() == null || !url.getRawQuery().toLowerCase(Locale.ROOT).contains("redirect_url="))
              && "https".equals(store.getScheme()) && store.getHost() != null
              && store.getUserInfo() == null && store.getQuery() == null && store.getFragment() == null,
          "INVALID_PREPAY_RESPONSE", "微信H5支付地址或商城返回地址无效");
      String target = store.toString().replaceAll("/+$", "") + "/orders/"
          + URLEncoder.encode(no, StandardCharsets.UTF_8);
      return value + (url.getRawQuery() == null ? "?" : "&") + "redirect_url="
          + URLEncoder.encode(target, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException error) {
      throw new Problem(502, "INVALID_PREPAY_RESPONSE", "微信未返回有效H5支付地址");
    }
  }
}
