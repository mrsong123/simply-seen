package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** HTTP controller helper that never logs bodies, OTPs, credentials or identity inputs. */
final class ProductionProtocolApi {
  record Member(String token, long id, String invite) {}
  private final MockMvc http;
  private final ObjectMapper json;
  private final AtomicInteger actors = new AtomicInteger();
  final ProductionProtocolProvider supplier = ProductionProtocolConfiguration.PROVIDER;

  ProductionProtocolApi(MockMvc http, ObjectMapper json) { this.http = http; this.json = json; }

  @SuppressWarnings("unchecked")
  Object call(String method, String path, Object body, String token, int expected) throws Exception {
    var request = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), "/api/v1" + path)
        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", UUID.randomUUID().toString())
        .with(servlet -> { servlet.setRemoteAddr("192.0.2.44"); return servlet; });
    if (body != null) request.content(json.writeValueAsString(body));
    if (token != null) request.header("Authorization", "Bearer " + token);
    var response = http.perform(request).andReturn().getResponse();
    Map<String, Object> decoded = json.readValue(response.getContentAsString(), new TypeReference<>() {});
    assertEquals(expected, response.getStatus(), method + " " + path + " code=" + decoded.get("code"));
    if (expected != 200) return decoded;
    assertEquals("OK", decoded.get("code"), method + " " + path);
    return decoded.get("data");
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> map(String method, String path, Object body, String token) throws Exception {
    return (Map<String, Object>) call(method, path, body, token, 200);
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> rows(String path, String token) throws Exception {
    return (List<Map<String, Object>>) call("GET", path, null, token, 200);
  }

  Member phone(String phone, String invite, boolean promoter) throws Exception {
    // Sending/consuming occurs through the production controller and real signed HTTPS RPC.
    int actor = actors.incrementAndGet();
    var request = MockMvcRequestBuilders.post("/api/v1/auth/phone/code")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("phone", phone)))
        .with(servlet -> { servlet.setRemoteAddr("192.0.2." + (60 + actor)); return servlet; });
    var response = http.perform(request).andReturn().getResponse();
    assertEquals(200, response.getStatus(), "production SMS challenge");
    var envelope = json.readValue(response.getContentAsString(), new TypeReference<Map<String, Object>>() {});
    @SuppressWarnings("unchecked") var challenge = (Map<String, Object>) envelope.get("data");
    assertEquals(false, challenge.get("development"));
    assertFalse(challenge.containsKey("devCode"));
    String code = Objects.requireNonNull(supplier.smsCodes.get(phone));
    var login = new HashMap<String, Object>();
    login.put("phone", phone); login.put("code", code); login.put("challengeId", challenge.get("challengeId"));
    login.put("inviteCode", invite); login.put("accepted", true);
    var session = map("POST", "/auth/phone", login, null);
    @SuppressWarnings("unchecked") var profile = (Map<String, Object>) session.get("profile");
    var member = new Member(String.valueOf(session.get("token")), ((Number) profile.get("id")).longValue(), String.valueOf(profile.get("invite_code")));
    if (promoter) call("POST", "/me/promoter", Map.of("accepted", true), member.token(), 200);
    return member;
  }

  String admin(String username, String password) throws Exception {
    return String.valueOf(map("POST", "/auth/admin", Map.of("username", username, "password", password), null).get("token"));
  }

  void callback(String kind, Map<String, Object> proof, boolean validSignature) throws Exception {
    callback(kind, proof, validSignature, validSignature ? 200 : 0);
  }

  void callback(String kind, Map<String, Object> proof, boolean validSignature, int expected) throws Exception {
    String body = supplier.encode(Map.of("id", UUID.randomUUID().toString(), "event_type", kind.toUpperCase(Locale.ROOT) + ".SUCCESS",
        "resource", supplier.keys.encryptedResource(supplier.encode(proof))));
    var request = MockMvcRequestBuilders.post("/api/v1/callbacks/wechat/" + kind)
        .contentType(MediaType.APPLICATION_JSON).content(body);
    for (var header : supplier.keys.signedHeaders(body).entrySet()) request.header(header.getKey(), header.getValue());
    if (!validSignature) {
      request.header("Wechatpay-Signature", "invalid");
      // Replace rather than append a second valid header.
      request.with(servlet -> { servlet.removeHeader("Wechatpay-Signature"); servlet.addHeader("Wechatpay-Signature", "invalid"); return servlet; });
    }
    var response = http.perform(request).andReturn().getResponse();
    if (!validSignature) { assertTrue(response.getStatus() >= 400 && response.getStatus() < 500); return; }
    if (expected != 200) { assertEquals(expected, response.getStatus(), kind + " evidence rejection"); return; }
    assertEquals(200, response.getStatus(), kind + " signed encrypted notification");
    Map<String, Object> decoded = json.readValue(response.getContentAsString(), new TypeReference<>() {});
    assertEquals("SUCCESS", decoded.get("code"));
  }
}
