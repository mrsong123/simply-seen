package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WechatSecurityTest {
  @TempDir Path dir;
  WechatClient channel;
  PrivateKey privateKey;
  final String secret = "12345678901234567890123456789012";

  @BeforeEach
  void setup() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    privateKey = pair.getPrivate();
    Path publicFile = dir.resolve("public.pem");
    Files.writeString(
        publicFile,
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----");
    Settings c =
        new Settings() {
          public String get(String key, String fallback) {
            return switch (key) {
              case "WX_PLATFORM_SERIAL" -> "TEST-SERIAL";
              case "WX_PLATFORM_PUBLIC_KEY_PATH" -> publicFile.toString();
              case "WX_API_V3_KEY" -> secret;
              default -> fallback;
            };
          }
        };
    channel = new WechatClient(c, new JsonCodec(new ObjectMapper()));
  }

  String signed(String ts, String nonce, String body) throws Exception {
    var s = Signature.getInstance("SHA256withRSA");
    s.initSign(privateKey);
    s.update((ts + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(s.sign());
  }

  @Test
  void acceptsValidSignatureAndRejectsTampering() throws Exception {
    String ts = "" + Instant.now().getEpochSecond(),
        body = "{\"id\":\"event\"}",
        sig = signed(ts, "nonce", body);
    assertDoesNotThrow(() -> channel.verify("TEST-SERIAL", ts, "nonce", sig, body));
    assertThrows(Problem.class, () -> channel.verify("TEST-SERIAL", ts, "nonce", sig, body + " "));
  }

  @Test
  void rejectsExpiredNotificationEvenWithValidSignature() throws Exception {
    String ts = "" + (Instant.now().getEpochSecond() - 301), sig = signed(ts, "nonce", "{}");
    assertThrows(Problem.class, () -> channel.verify("TEST-SERIAL", ts, "nonce", sig, "{}"));
  }

  @Test
  void rejectsUnexpectedPlatformKeySerial() throws Exception {
    String ts = "" + Instant.now().getEpochSecond(), sig = signed(ts, "nonce", "{}");
    assertThrows(Problem.class, () -> channel.verify("WRONG-SERIAL", ts, "nonce", sig, "{}"));
  }

  @Test
  void decryptsAuthenticatedCallbackAndRejectsAlteredCiphertext() throws Exception {
    String nonce = "abcdefghijkl", aad = "transaction";
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "AES"),
        new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
    c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
    String encrypted =
        Base64.getEncoder()
            .encodeToString(
                c.doFinal("{\"trade_state\":\"SUCCESS\"}".getBytes(StandardCharsets.UTF_8)));
    var json = new ObjectMapper();
    String body =
        json.writeValueAsString(
            Map.of(
                "resource",
                Map.of(
                    "algorithm",
                    "AEAD_AES_256_GCM",
                    "nonce",
                    nonce,
                    "associated_data",
                    aad,
                    "ciphertext",
                    encrypted)));
    assertEquals("SUCCESS", channel.decrypt(body).get("trade_state"));
    String bad =
        json.writeValueAsString(
            Map.of(
                "resource",
                Map.of(
                    "algorithm",
                    "AEAD_AES_256_GCM",
                    "nonce",
                    nonce,
                    "associated_data",
                    "wrong",
                    "ciphertext",
                    encrypted)));
    assertThrows(Problem.class, () -> channel.decrypt(bad));
  }

  @Test
  void disallowsUnencryptedExternalEndpoints() {
    assertThrows(Problem.class, () -> channel.request("GET", "http://insecure.invalid", "", false));
  }
}
