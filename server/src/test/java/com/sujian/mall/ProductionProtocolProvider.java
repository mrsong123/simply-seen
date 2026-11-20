package com.sujian.mall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Local certificate-validated official-contract supplier, only compiled into tests. */
final class ProductionProtocolProvider implements AutoCloseable {
  static final String ACCESS_ID = "protocol-test-access-id";
  static final String ACCESS_SECRET = "protocol-test-access-secret";
  final ProductionProtocolKeys keys;
  final ProductionProtocolWechat wechat;
  final HttpsServer server;
  final HttpClient client;
  final ObjectMapper json = new ObjectMapper();
  final Map<String, String> smsCodes = new ConcurrentHashMap<>();
  final AtomicInteger rpcRequests = new AtomicInteger();
  final AtomicInteger signedRequests = new AtomicInteger();
  private final ExecutorService executor = Executors.newCachedThreadPool();

  ProductionProtocolProvider() throws Exception {
    keys = new ProductionProtocolKeys();
    wechat = new ProductionProtocolWechat(keys);
    server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(keys.tls));
    server.setExecutor(executor);
    server.createContext("/", this::handle);
    server.start();
    client = HttpClient.newBuilder().sslContext(keys.tls)
        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER)
        .proxy(new ProxySelector() {
          public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
          public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) {}
        }).build();
  }

  String origin() { return "https://localhost:" + server.getAddress().getPort(); }

  Map<String, Object> decode(String value) throws Exception {
    return json.readValue(value, new TypeReference<>() {});
  }

  String encode(Object value) throws Exception { return json.writeValueAsString(value); }

  private void handle(HttpExchange exchange) {
    boolean signed = exchange.getRequestURI().getPath().startsWith("/v3/");
    try {
      String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      Object response;
      if (exchange.getRequestURI().getPath().equals("/login")) {
        var query = form(exchange.getRequestURI().getRawQuery());
        require(ProductionProtocolWechat.APP.equals(query.get("appid")), "login app");
        require("protocol-app-secret".equals(query.get("secret")), "login secret");
        require(query.getOrDefault("js_code", "").startsWith("wx-"), "login code");
        response = Map.of("openid", "openid-" + query.get("js_code"));
      } else if (signed) {
        verifyMerchant(exchange, raw);
        var headers = new HashMap<String, String>();
        headers.put("Wechatpay-Serial", exchange.getRequestHeaders().getFirst("Wechatpay-Serial"));
        response = wechat.request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
            raw.isBlank() ? Map.of() : decode(raw), headers);
      } else response = rpc(form(raw));
      String body = response == null ? "" : encode(response);
      send(exchange, response == null ? 204 : 200, body, signed);
    } catch (Throwable failure) {
      try { send(exchange, 400, "{\"code\":\"PROTOCOL_REQUEST_REJECTED\"}", signed); }
      catch (Exception ignored) { exchange.close(); }
    }
  }

  @SuppressWarnings("unchecked")
  private Object rpc(Map<String, String> fields) throws Exception {
    require(ACCESS_ID.equals(fields.get("AccessKeyId")), "rpc access id");
    require("HMAC-SHA1".equals(fields.get("SignatureMethod")), "rpc algorithm");
    require("1.0".equals(fields.get("SignatureVersion")), "rpc signature version");
    var signed = new TreeMap<>(fields);
    String actual = signed.remove("Signature");
    String canonical = signed.entrySet().stream()
        .map(entry -> encodeField(entry.getKey()) + "=" + encodeField(entry.getValue()))
        .reduce((left, right) -> left + "&" + right).orElse("");
    var mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec((ACCESS_SECRET + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
    String expected = Base64.getEncoder().encodeToString(mac.doFinal(
        ("POST&%2F&" + encodeField(canonical)).getBytes(StandardCharsets.UTF_8)));
    require(actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
        actual.getBytes(StandardCharsets.UTF_8)), "rpc signature");
    rpcRequests.incrementAndGet();
    String id = UUID.randomUUID().toString();
    if ("SendSms".equals(fields.get("Action"))) {
      require("2017-05-25".equals(fields.get("Version")), "sms version");
      require("协议短信".equals(fields.get("SignName")), "sms sign name");
      require("PROTOCOL_TEMPLATE".equals(fields.get("TemplateCode")), "sms template");
      var parameters = decode(fields.get("TemplateParam"));
      String code = String.valueOf(parameters.get("code"));
      require(code.matches("[0-9]{6}"), "sms code shape");
      smsCodes.put(fields.get("PhoneNumbers"), code);
      return Map.of("Code", "OK", "RequestId", id, "BizId", "protocol-message");
    }
    require("Id2MetaVerify".equals(fields.get("Action")), "identity action");
    require("2019-03-07".equals(fields.get("Version")), "identity version");
    require("normal".equals(fields.get("ParamType")), "identity parameter type");
    require(fields.getOrDefault("IdentifyNum", "").matches("[0-9]{17}[0-9X]"), "identity shape");
    return Map.of("Code", "200", "RequestId", id, "ResultObject", Map.of("BizCode", "1"));
  }

  private void verifyMerchant(HttpExchange exchange, String body) throws Exception {
    String header = exchange.getRequestHeaders().getFirst("Authorization");
    require(header != null && header.startsWith("WECHATPAY2-SHA256-RSA2048 "), "merchant auth");
    var values = new HashMap<String, String>();
    var matcher = Pattern.compile("([a-z_]+)=\"([^\"]+)\"").matcher(header);
    while (matcher.find()) values.put(matcher.group(1), matcher.group(2));
    require(ProductionProtocolWechat.MCH.equals(values.get("mchid")), "merchant id");
    require("PROTOCOL_MERCHANT_SERIAL".equals(values.get("serial_no")), "merchant serial");
    require(Math.abs(Instant.now().getEpochSecond() - Long.parseLong(values.get("timestamp"))) <= 300,
        "merchant timestamp");
    String message = exchange.getRequestMethod() + "\n" + exchange.getRequestURI() + "\n"
        + values.get("timestamp") + "\n" + values.get("nonce_str") + "\n" + body + "\n";
    require(ProductionProtocolKeys.verify(keys.merchant.getPublic(), message, values.get("signature")),
        "merchant request signature");
    signedRequests.incrementAndGet();
  }

  private void send(HttpExchange exchange, int status, String body, boolean signed) throws Exception {
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
    if (signed) for (var field : keys.signedHeaders(body).entrySet())
      exchange.getResponseHeaders().set(field.getKey(), field.getValue());
    byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, status == 204 ? -1 : encoded.length);
    try (var output = exchange.getResponseBody()) { if (status != 204) output.write(encoded); }
  }

  private static Map<String, String> form(String body) {
    var result = new LinkedHashMap<String, String>();
    if (body == null || body.isBlank()) return result;
    for (String part : body.split("&")) {
      int at = part.indexOf('=');
      if (at >= 0) result.put(URLDecoder.decode(part.substring(0, at), StandardCharsets.UTF_8),
          URLDecoder.decode(part.substring(at + 1), StandardCharsets.UTF_8));
    }
    return result;
  }

  private static String encodeField(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
        .replace("*", "%2A").replace("%7E", "~");
  }

  private static void require(boolean condition, String field) {
    if (!condition) throw new IllegalArgumentException("Protocol validation: " + field);
  }

  public void close() { server.stop(0); executor.shutdownNow(); client.close(); }
}
