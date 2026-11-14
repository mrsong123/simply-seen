package com.sujian.mall.integration.aliyun;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujian.mall.common.JsonCodec;
import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class AliyunRpcClientTest {
  private Settings settings(String endpoint) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    environment
        .withProperty("SMS_API_URL", endpoint)
        .withProperty("SMS_ACCESS_KEY_ID", "test-access")
        .withProperty("SMS_ACCESS_KEY_SECRET", "test-secret")
        .withProperty("SMS_REGION_ID", "cn-hangzhou");
    return new Settings(environment);
  }

  @Test
  void matchesIndependentRpcHmacVectorAndRfc3986Encoding() {
    var parameters = new TreeMap<String, String>();
    parameters.put("AccessKeyId", "test-access");
    parameters.put("Action", "SendSms");
    parameters.put("Format", "JSON");
    parameters.put("PhoneNumbers", "13800138000");
    parameters.put("RegionId", "cn-hangzhou");
    parameters.put("SignName", "素见 好物");
    parameters.put("SignatureMethod", "HMAC-SHA1");
    parameters.put("SignatureNonce", "nonce-20261114");
    parameters.put("SignatureVersion", "1.0");
    parameters.put("TemplateCode", "SMS_TEST");
    parameters.put("TemplateParam", "{\"code\":\"123456\"}");
    parameters.put("Timestamp", "2026-11-14T00:00:00Z");
    parameters.put("Version", "2017-05-25");
    // Frozen vector calculated independently with Python hmac/urllib using the RPC specification.
    assertEquals("dxDcIosRywhAWI4NpApl7kiFwpk=", AliyunRpcSigner.sign(parameters, "test-secret"));
    assertEquals("%20%2A~%2B", AliyunRpcSigner.encode(" *~+"));
    parameters.put("Signature", "old-value");
    assertEquals("dxDcIosRywhAWI4NpApl7kiFwpk=", AliyunRpcSigner.sign(parameters, "test-secret"));
  }

  @Test
  void sendsSensitiveFieldsInPostBodyWithUniqueNonceAndUtcTimestamp() {
    var seen = new AtomicReference<Map<String, String>>();
    var uri = new AtomicReference<java.net.URI>();
    var rpc =
        new AliyunRpcClient(
            settings("https://sms.example.test"),
            new JsonCodec(new ObjectMapper()),
            (url, body) -> {
              uri.set(url);
              seen.set(parse(body));
              return new AliyunRpcTransport.Response(200, "{\"Code\":\"OK\"}");
            });
    rpc.call("SMS", "SendSms", "2017-05-25", Map.of("PhoneNumbers", "13800138000"));
    Map<String, String> first = seen.get();
    assertNull(uri.get().getRawQuery());
    assertEquals("/", uri.get().getPath());
    assertEquals("13800138000", first.get("PhoneNumbers"));
    assertEquals("SendSms", first.get("Action"));
    assertEquals("HMAC-SHA1", first.get("SignatureMethod"));
    assertEquals("1.0", first.get("SignatureVersion"));
    assertTrue(first.get("Timestamp").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
    assertEquals(AliyunRpcSigner.sign(first, "test-secret"), first.get("Signature"));
    rpc.call("SMS", "SendSms", "2017-05-25", Map.of("PhoneNumbers", "13800138000"));
    assertNotEquals(first.get("SignatureNonce"), seen.get().get("SignatureNonce"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://sms.example.test", "https://user:secret@sms.example.test/", "https://sms.example.test/?token=secret", "https://sms.example.test/path", "https://sms.example.test/#secret"})
  void rejectsUnsafeEndpointBeforeCallingTransport(String endpoint) {
    var rpc =
        new AliyunRpcClient(
            settings(endpoint),
            new JsonCodec(new ObjectMapper()),
            (url, body) -> {
              fail("Transport must not run for an invalid endpoint");
              return null;
            });
    assertEquals(
        "ACCOUNT_PROVIDER_ENDPOINT",
        assertThrows(Problem.class, () -> rpc.call("SMS", "SendSms", "2017-05-25", Map.of())).code);
  }

  @ParameterizedTest
  @ValueSource(ints = {301, 400, 401, 429, 500})
  void doesNotAcceptErrorOrRedirectResponse(int status) {
    var rpc = client(status, "{\"Code\":\"OK\",\"Message\":\"echo-sensitive-data\"}");
    Problem problem =
        assertThrows(Problem.class, () -> rpc.call("SMS", "SendSms", "2017-05-25", Map.of()));
    assertFalse(problem.getMessage().contains("echo-sensitive-data"));
    assertEquals(503, problem.status);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not JSON with identity data", "[]", "null"})
  void malformedJsonNeverExposesPayloadOrExceptionCause(String body) {
    var rpc = client(200, body);
    Problem problem =
        assertThrows(Problem.class, () -> rpc.call("SMS", "SendSms", "2017-05-25", Map.of()));
    assertEquals("ACCOUNT_PROVIDER_RESPONSE", problem.code);
    assertNull(problem.getCause());
    assertFalse(problem.getMessage().contains(body));
  }

  @Test
  void responseSubscriberCancelsOversizedResponse() {
    var subscriber = new BoundedJsonSubscriber();
    var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    subscriber.onSubscribe(
        new Flow.Subscription() {
          public void request(long n) {}
          public void cancel() { cancelled.set(true); }
        });
    subscriber.onNext(List.of(ByteBuffer.allocate(65537)));
    assertTrue(cancelled.get());
    assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
  }

  @Test
  void responseSubscriberDecodesUtf8AcrossChunks() {
    var subscriber = new BoundedJsonSubscriber();
    subscriber.onSubscribe(new Flow.Subscription() {
      public void request(long n) {}
      public void cancel() {}
    });
    byte[] bytes = "{\"Message\":\"成功\"}".getBytes(StandardCharsets.UTF_8);
    subscriber.onNext(List.of(ByteBuffer.wrap(bytes, 0, 14)));
    subscriber.onNext(List.of(ByteBuffer.wrap(bytes, 14, bytes.length - 14)));
    subscriber.onComplete();
    assertEquals("{\"Message\":\"成功\"}", subscriber.getBody().toCompletableFuture().join());
  }

  private AliyunRpcClient client(int status, String body) {
    return new AliyunRpcClient(
        settings("https://sms.example.test/"),
        new JsonCodec(new ObjectMapper()),
        (url, request) -> new AliyunRpcTransport.Response(status, body));
  }

  static Map<String, String> parse(String form) {
    var map = new HashMap<String, String>();
    for (String item : form.split("&")) {
      String[] parts = item.split("=", 2);
      map.put(
          URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
          URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
    }
    return map;
  }
}
