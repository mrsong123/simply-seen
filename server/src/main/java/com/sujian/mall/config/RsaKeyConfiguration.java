package com.sujian.mall.config;

import java.nio.file.*;
import java.security.KeyFactory;
import java.security.interfaces.RSAKey;
import java.security.spec.*;
import java.util.Base64;
import java.util.List;

final class RsaKeyConfiguration {
  private RsaKeyConfiguration() {}
  static void validate(Settings settings, List<String> errors) {
    parse(settings, "WX_PRIVATE_KEY_PATH", true, errors);
    parse(settings, "WX_PLATFORM_PUBLIC_KEY_PATH", false, errors);
  }
  private static void parse(Settings settings, String name, boolean secret, List<String> errors) {
    String value = ConfigurationChecks.required(settings, name, errors);
    if (value.isBlank()) return;
    try {
      Path path = Path.of(value);
      if (!Files.isReadable(path) || !Files.isRegularFile(path) || Files.size(path) > 65536)
        throw new IllegalArgumentException();
      RSAKey key = (RSAKey) (secret
          ? com.sujian.mall.common.RsaKeyFiles.readPrivate(path)
          : com.sujian.mall.common.RsaKeyFiles.readPublic(path));
      if (key.getModulus().bitLength() < 2048) throw new IllegalArgumentException();
    } catch (Exception failure) {
      errors.add(name + "必须指向可读取的2048位或以上RSA " + (secret ? "PKCS#8/PKCS#1私钥PEM" : "公钥PEM或X509证书"));
    }
  }
}
