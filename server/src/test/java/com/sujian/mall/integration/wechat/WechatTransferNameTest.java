package com.sujian.mall.integration.wechat;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WechatTransferNameTest {
  @TempDir Path folder;
  @Test void nameIsOaepEncryptedAndPlatformSerialTravelsInHeader() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
    var pair = generator.generateKeyPair();
    Path publicKey = folder.resolve("platform-public.pem");
    Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n"
        + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
    Map<String, String> values = Map.of("WX_PLATFORM_PUBLIC_KEY_PATH", publicKey.toString(),
        "WX_PLATFORM_SERIAL", "PUB_KEY_ID_UNIT", "WX_APP_ID", "unit-app",
        "WX_TRANSFER_SCENE_ID", "unit-scene", "WX_TRANSFER_NOTIFY_URL", "https://shop.example/transfer",
        "WX_TRANSFER_PERCEPTION", "推广佣金", "WX_TRANSFER_REPORT_INFOS", "[]",
        "WX_TRANSFER_BASE_URL", "https://payments.example");
    var settings = new Settings() { @Override public String get(String name, String fallback) { return values.getOrDefault(name, fallback); } };
    var json = new JsonCodec(new ObjectMapper());
    List<Map<String, Object>> bodies = new ArrayList<>();
    List<Map<String, String>> headers = new ArrayList<>();
    var api = new WechatClient(settings, json) {
      @Override protected Map<String, Object> request(String method, String uri, String payload,
          boolean signed, Map<String, String> additionalHeaders) {
        assertEquals("POST", method); assertTrue(signed);
        bodies.add(json.decode(payload)); headers.add(additionalHeaders);
        return Map.of("out_bill_no", "WD1", "transfer_bill_no", "WX1", "state", "WAIT_USER_CONFIRM");
      }
    };
    api.transfer(Map.of("id", "WD1", "net", 200000), "wx-recipient", "张三");
    String encrypted = bodies.getFirst().get("user_name").toString();
    assertNotEquals("张三", encrypted); assertEquals("PUB_KEY_ID_UNIT", headers.getFirst().get("Wechatpay-Serial"));
    Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
    cipher.init(Cipher.DECRYPT_MODE, pair.getPrivate(),
        new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));
    assertEquals("张三", new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8));
    assertEquals("wx-recipient", bodies.getFirst().get("openid"));
    assertEquals("TRANSFER_NAME_REQUIRED", assertThrows(Problem.class,
        () -> api.transfer(Map.of("id", "WD2", "net", 200000), "wx-recipient")).code);
  }
}
