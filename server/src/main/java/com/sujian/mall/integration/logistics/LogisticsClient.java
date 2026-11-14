package com.sujian.mall.integration.logistics;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LogisticsClient {
  private final Settings settings;
  private final JsonCodec db;

  public LogisticsClient(Settings settings, JsonCodec db) {
    this.settings = settings;
    this.db = db;
  }

  public Object query(Map<String, Object> order) {
    Problem.require(!s(order, "tracking_no").isBlank(), "NOT_SHIPPED", "订单尚未发货");
    String endpoint = settings.required("LOGISTICS_QUERY_URL");
    Problem.require(endpoint.startsWith("https://"), "INSECURE_ENDPOINT", "物流接口必须使用HTTPS");
    var codes = db.decode(settings.required("LOGISTICS_CARRIER_CODES"));
    String code = s(codes, s(order, "carrier"));
    Problem.require(!code.isBlank(), "CARRIER_NOT_CONFIGURED", "该物流公司的服务商编码尚未配置");
    String customer = settings.required("LOGISTICS_CUSTOMER");
    var address = db.decode(s(order, "address_snapshot"));
    String param =
        db.encode(
            Map.of(
                "com",
                code,
                "num",
                s(order, "tracking_no"),
                "phone",
                s(address, "phone"),
                "resultv2",
                "4",
                "show",
                "0",
                "order",
                "desc"));
    try {
      String sign =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("MD5")
                      .digest(
                          (param + settings.required("LOGISTICS_KEY") + customer)
                              .getBytes(StandardCharsets.UTF_8)))
              .toUpperCase(Locale.ROOT);
      String form = "customer=" + encode(customer) + "&sign=" + sign + "&param=" + encode(param);
      var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
      String proxy = System.getenv("HTTPS_PROXY");
      if (proxy != null) {
        var p = URI.create(proxy);
        builder.proxy(ProxySelector.of(new InetSocketAddress(p.getHost(), p.getPort())));
      }
      var request =
          HttpRequest.newBuilder(URI.create(endpoint))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(form))
              .build();
      var result = builder.build().send(request, HttpResponse.BodyHandlers.ofString());
      Problem.require(result.statusCode() == 200, "LOGISTICS_UNAVAILABLE", "物流服务暂时不可用");
      return tracks(db.decode(result.body()));
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(502, "LOGISTICS_UNAVAILABLE", "物流服务暂时不可用，请稍后重试");
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> tracks(Map<String, Object> result) {
    Problem.require(s(result, "status").equals("200"), "LOGISTICS_NOT_FOUND", "物流公司尚未返回轨迹，请稍后查询");
    var rows = (List<Map<String, Object>>) result.get("data");
    if (rows == null) return List.of();
    return rows.stream()
        .limit(100)
        .map(row -> Map.<String, Object>of("time", s(row, "time"), "context", s(row, "context")))
        .toList();
  }
}
