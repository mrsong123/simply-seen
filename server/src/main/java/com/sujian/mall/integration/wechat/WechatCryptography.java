package com.sujian.mall.integration.wechat;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** WeChat request signing and trusted platform evidence verification. */
final class WechatCryptography {
  private final Settings c;
  private final JsonCodec d;

  WechatCryptography(Settings settings, JsonCodec json) { c = settings; d = json; }

  private PrivateKey privateKey() {
    try {
      return RsaKeyFiles.readPrivate(Path.of(c.required("WX_PRIVATE_KEY_PATH")));
    } catch (Exception e) {
      throw new Problem(503, "KEY_CONFIGURATION", "微信商户私钥不可用");
    }
  }

  private PublicKey publicKey() {
    try {
      return RsaKeyFiles.readPublic(Path.of(c.required("WX_PLATFORM_PUBLIC_KEY_PATH")));
    } catch (Exception e) {
      throw new Problem(503, "KEY_CONFIGURATION", "微信平台公钥不可用");
    }
  }

  String encryptName(String value) {
    try {
      Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, publicKey(),
          new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT));
      return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new Problem(503, "TRANSFER_NAME_ENCRYPTION", "微信收款姓名加密失败，请核对平台公钥配置");
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
