package com.sujian.mall;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;

/** Ephemeral test-only CA and merchant/platform keys; no live credentials are used. */
final class ProductionProtocolKeys {
  static final String V3_KEY = "0123456789abcdef0123456789abcdef";
  static final String PLATFORM_SERIAL = "PROTOCOL_PLATFORM_SERIAL";
  final KeyPair merchant;
  final KeyPair platform;
  final Path directory;
  final SSLContext tls;

  ProductionProtocolKeys() throws Exception {
    directory = Files.createTempDirectory("sujian-production-protocol-");
    var rsa = KeyPairGenerator.getInstance("RSA");
    rsa.initialize(2048);
    merchant = rsa.generateKeyPair();
    platform = rsa.generateKeyPair();
    pem(directory.resolve("merchant.pem"), "PRIVATE KEY", merchant.getPrivate().getEncoded());
    pem(directory.resolve("platform.pem"), "PUBLIC KEY", platform.getPublic().getEncoded());
    String password = UUID.randomUUID().toString();
    Path store = directory.resolve("local-ca.p12");
    String executable = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    var process = new ProcessBuilder(executable, "-genkeypair", "-alias", "protocol", "-keyalg", "RSA",
        "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost", "-ext",
        "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12", "-keystore", store.toString(),
        "-storepass", password, "-keypass", password, "-noprompt")
        .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
    if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0)
      throw new IllegalStateException("Cannot create the isolated protocol-test certificate");
    var keyStore = KeyStore.getInstance("PKCS12");
    try (var input = Files.newInputStream(store)) { keyStore.load(input, password.toCharArray()); }
    var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(keyStore, password.toCharArray());
    var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(keyStore);
    tls = SSLContext.getInstance("TLS");
    tls.init(keys.getKeyManagers(), trust.getTrustManagers(), new SecureRandom());
  }

  private void pem(Path path, String label, byte[] data) throws Exception {
    Files.writeString(path, "-----BEGIN " + label + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(data)
        + "\n-----END " + label + "-----\n");
  }

  static String sign(PrivateKey key, String content) throws Exception {
    var signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(key);
    signer.update(content.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(signer.sign());
  }

  static boolean verify(PublicKey key, String content, String signature) throws Exception {
    var verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(key);
    verifier.update(content.getBytes(StandardCharsets.UTF_8));
    return verifier.verify(Base64.getDecoder().decode(signature));
  }

  Map<String, String> signedHeaders(String body) throws Exception {
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    String nonce = UUID.randomUUID().toString();
    return Map.of("Wechatpay-Serial", PLATFORM_SERIAL, "Wechatpay-Timestamp", timestamp,
        "Wechatpay-Nonce", nonce, "Wechatpay-Signature",
        sign(platform.getPrivate(), timestamp + "\n" + nonce + "\n" + body + "\n"));
  }

  Map<String, Object> encryptedResource(String plaintext) throws Exception {
    String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String aad = "protocol-notification";
    var cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(V3_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
        new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
    return Map.of("algorithm", "AEAD_AES_256_GCM", "nonce", nonce, "associated_data", aad,
        "ciphertext", Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8))));
  }

  String decryptName(String encrypted) throws Exception {
    var cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
    cipher.init(Cipher.DECRYPT_MODE, platform.getPrivate());
    return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
  }
}
