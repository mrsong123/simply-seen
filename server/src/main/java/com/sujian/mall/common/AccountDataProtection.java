package com.sujian.mall.common;

import com.sujian.mall.config.Settings;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Encrypts private account fields; deterministic fingerprints are purpose-separated HMACs. */
@Component
public class AccountDataProtection {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Map<Path, Object> LOCAL_LOCKS = new ConcurrentHashMap<>();
  private static final byte[] AAD = "sujian-account-data:v1".getBytes(StandardCharsets.UTF_8);
  private final Settings settings;
  private volatile byte[] material;

  public AccountDataProtection(Settings settings) {
    this.settings = settings;
  }

  public String encrypt(String value) {
    try {
      byte[] nonce = new byte[12];
      RANDOM.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(AAD);
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(
          ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
    } catch (Problem problem) {
      throw problem;
    } catch (Exception exception) {
      throw unavailable();
    }
  }

  public String decrypt(String value) {
    try {
      if (!value.startsWith("v1.")) throw unavailable();
      byte[] bytes = Base64.getUrlDecoder().decode(value.substring(3));
      if (bytes.length < 29) throw unavailable();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"),
          new GCMParameterSpec(128, Arrays.copyOfRange(bytes, 0, 12)));
      cipher.updateAAD(AAD);
      return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw unavailable();
    }
  }

  public String fingerprint(String context, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key(), "HmacSHA256"));
      return HexFormat.of().formatHex(
          mac.doFinal((context + "\0" + value).getBytes(StandardCharsets.UTF_8)));
    } catch (Problem problem) {
      throw problem;
    } catch (Exception exception) {
      throw unavailable();
    }
  }

  private byte[] key() {
    if (material != null) return material;
    synchronized (this) {
      if (material != null) return material;
      String configured = settings.get("ACCOUNT_DATA_KEY", "");
      if (configured.isBlank()) {
        if (!settings.dev()) throw new Problem(503, "ACCOUNT_KEY_REQUIRED", "尚未配置账户数据加密密钥");
        configured = localKey();
      }
      try {
        byte[] decoded = Base64.getDecoder().decode(configured.trim());
        if (decoded.length != 32) throw unavailable();
        material = decoded;
        return material;
      } catch (IllegalArgumentException exception) {
        throw unavailable();
      }
    }
  }

  private String localKey() {
    Path path = Path.of(settings.get("ACCOUNT_DATA_KEY_PATH", "./data/account-data.key"))
        .toAbsolutePath().normalize();
    synchronized (LOCAL_LOCKS.computeIfAbsent(path, ignored -> new Object())) {
      try {
        boolean posix = path.getFileSystem().supportedFileAttributeViews().contains("posix");
        Path parent = path.getParent();
        if (posix) Files.createDirectories(parent,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        else Files.createDirectories(parent);
        Path lockPath = path.resolveSibling(path.getFileName() + ".lock");
        try {
          if (posix) Files.createFile(lockPath,
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
          else Files.createFile(lockPath);
        } catch (FileAlreadyExistsException ignored) {
          // Another local process already created the lock file.
        }
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
            var lock = channel.lock()) {
          if (Files.isSymbolicLink(path)) throw unavailable();
          if (!Files.exists(path)) {
            byte[] generated = new byte[32];
            RANDOM.nextBytes(generated);
            Path temporary = posix
                ? Files.createTempFile(parent, ".account-key-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                : Files.createTempFile(parent, ".account-key-", ".tmp");
            try {
              Files.writeString(temporary, Base64.getEncoder().encodeToString(generated),
                  StandardOpenOption.WRITE);
              Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } finally {
              Files.deleteIfExists(temporary);
            }
          }
          if (posix) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
          return Files.readString(path).trim();
        }
      } catch (Problem problem) {
        throw problem;
      } catch (Exception exception) {
        throw unavailable();
      }
    }
  }

  private Problem unavailable() {
    return new Problem(503, "ACCOUNT_DATA_PROTECTION", "账户数据加密配置无效或数据无法读取");
  }
}
