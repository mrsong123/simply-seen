package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;
import com.sujian.mall.common.RsaKeyFiles;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RsaKeyFilesTest {
  @TempDir Path directory;
  private Path pem(String type, byte[] bytes) throws Exception {
    var file = directory.resolve(type.replace(' ', '-') + ".pem");
    Files.writeString(file, "-----BEGIN " + type + "-----\n"
        + Base64.getEncoder().encodeToString(bytes) + "\n-----END " + type + "-----\n");
    return file;
  }
  private byte[] sequence(byte[]... fields) { var bytes = new ByteArrayOutputStream(); for (var field : fields) bytes.writeBytes(field); return der(48, bytes.toByteArray()); }
  private byte[] der(int tag, byte[] value) {
    var result = new ByteArrayOutputStream(); result.write(tag);
    if (value.length < 128) result.write(value.length);
    else { int bytes = (32 - Integer.numberOfLeadingZeros(value.length) + 7) / 8;
      result.write(128 | bytes); for (int i = bytes - 1; i >= 0; i--) result.write(value.length >>> (8 * i)); }
    result.writeBytes(value); return result.toByteArray();
  }
  @Test void pkcs8AndTraditionalRsaPemLoadSameKeyMaterial() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
    var pair = generator.generateKeyPair(); var privateKey = (RSAPrivateCrtKey) pair.getPrivate();
    byte[] traditional = sequence(der(2, new byte[] {0}), der(2, privateKey.getModulus().toByteArray()),
        der(2, privateKey.getPublicExponent().toByteArray()), der(2, privateKey.getPrivateExponent().toByteArray()),
        der(2, privateKey.getPrimeP().toByteArray()), der(2, privateKey.getPrimeQ().toByteArray()),
        der(2, privateKey.getPrimeExponentP().toByteArray()), der(2, privateKey.getPrimeExponentQ().toByteArray()),
        der(2, privateKey.getCrtCoefficient().toByteArray()));
    assertArrayEquals(pair.getPrivate().getEncoded(), RsaKeyFiles.readPrivate(pem("PRIVATE KEY", pair.getPrivate().getEncoded())).getEncoded());
    assertArrayEquals(pair.getPrivate().getEncoded(), RsaKeyFiles.readPrivate(pem("RSA PRIVATE KEY", traditional)).getEncoded());
    var publicKey = (RSAPublicKey) pair.getPublic();
    assertArrayEquals(pair.getPublic().getEncoded(), RsaKeyFiles.readPublic(pem("PUBLIC KEY", pair.getPublic().getEncoded())).getEncoded());
    byte[] publicTraditional = sequence(der(2, publicKey.getModulus().toByteArray()), der(2, publicKey.getPublicExponent().toByteArray()));
    assertArrayEquals(pair.getPublic().getEncoded(), RsaKeyFiles.readPublic(pem("RSA PUBLIC KEY", publicTraditional)).getEncoded());
  }
  @Test void weakUnsupportedAndOversizedKeyFilesFailClosed() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(1024);
    var weak = generator.generateKeyPair();
    assertThrows(GeneralSecurityException.class, () -> RsaKeyFiles.readPrivate(pem("PRIVATE KEY", weak.getPrivate().getEncoded())));
    assertThrows(GeneralSecurityException.class, () -> RsaKeyFiles.readPublic(pem("PUBLIC KEY", weak.getPublic().getEncoded())));
    assertThrows(GeneralSecurityException.class, () -> RsaKeyFiles.readPrivate(pem("ENCRYPTED PRIVATE KEY", new byte[5])));
    Path oversized = directory.resolve("large.pem"); Files.writeString(oversized, "x".repeat(65537));
    assertThrows(java.io.IOException.class, () -> RsaKeyFiles.readPublic(oversized));
  }
}
