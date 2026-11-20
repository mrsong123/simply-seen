package com.sujian.mall.common;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAKey;
import java.security.spec.*;
import java.util.*;

/** Shared runtime/preflight PEM parser for merchant and platform key formats. */
public final class RsaKeyFiles {
  private static final byte[] RSA_ALGORITHM = HexFormat.of().parseHex("300d06092a864886f70d0101010500");
  private RsaKeyFiles() {}

  public static PrivateKey readPrivate(Path path) throws IOException, GeneralSecurityException {
    String pem = read(path);
    byte[] bytes;
    if (pem.contains("-----BEGIN RSA PRIVATE KEY-----"))
      bytes = der(0x30, join(new byte[] {2, 1, 0}, RSA_ALGORITHM,
          der(4, decode(pem, "RSA PRIVATE KEY"))));
    else bytes = decode(pem, "PRIVATE KEY");
    PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    requireRsa(key);
    return key;
  }

  public static PublicKey readPublic(Path path) throws IOException, GeneralSecurityException {
    String pem = read(path);
    PublicKey key;
    if (pem.contains("-----BEGIN CERTIFICATE-----")) {
      var certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
          .generateCertificate(new ByteArrayInputStream(decode(pem, "CERTIFICATE")));
      certificate.checkValidity();
      key = certificate.getPublicKey();
    } else {
      byte[] bytes = pem.contains("-----BEGIN RSA PUBLIC KEY-----")
          ? der(0x30, join(RSA_ALGORITHM, der(3, join(new byte[] {0}, decode(pem, "RSA PUBLIC KEY")))))
          : decode(pem, "PUBLIC KEY");
      key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    }
    requireRsa(key);
    return key;
  }

  private static String read(Path path) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
      byte[] bytes = input.readNBytes(65537);
      if (bytes.length > 65536) throw new IOException("RSA key file exceeds supported size");
      return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }
  }

  private static byte[] decode(String pem, String kind) throws InvalidKeySpecException {
    String start = "-----BEGIN " + kind + "-----", end = "-----END " + kind + "-----";
    int from = pem.indexOf(start), to = pem.indexOf(end);
    if (from < 0 || to <= from || pem.indexOf(start, from + start.length()) >= 0)
      throw new InvalidKeySpecException("Unsupported or ambiguous PEM key format");
    try { return Base64.getDecoder().decode(pem.substring(from + start.length(), to).replaceAll("\\s", "")); }
    catch (IllegalArgumentException error) { throw new InvalidKeySpecException("Invalid PEM encoding"); }
  }

  private static void requireRsa(Key key) throws InvalidKeyException {
    if (!(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() < 2048)
      throw new InvalidKeyException("RSA keys must contain at least 2048 bits");
  }

  private static byte[] join(byte[]... fields) {
    var output = new ByteArrayOutputStream();
    for (byte[] field : fields) output.writeBytes(field);
    return output.toByteArray();
  }

  private static byte[] der(int tag, byte[] content) {
    var output = new ByteArrayOutputStream();
    output.write(tag);
    if (content.length < 128) output.write(content.length);
    else {
      int count = (32 - Integer.numberOfLeadingZeros(content.length) + 7) / 8;
      output.write(128 | count);
      for (int index = count - 1; index >= 0; index--) output.write(content.length >>> (index * 8));
    }
    output.writeBytes(content);
    return output.toByteArray();
  }
}
