package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountDataProtectionTest {
  @TempDir Path directory;

  private Settings settings(boolean development, String key) {
    return new Settings() {
      @Override public boolean dev() { return development; }
      @Override public String get(String name, String fallback) {
        return switch (name) {
          case "ACCOUNT_DATA_KEY" -> key;
          case "ACCOUNT_DATA_KEY_PATH" -> directory.resolve("account.key").toString();
          default -> fallback;
        };
      }
    };
  }

  @Test void localKeyIsPrivatePersistentAndGcmUsesFreshNonces() throws Exception {
    var first = new AccountDataProtection(settings(true, ""));
    String value = "13800138000";
    String encrypted = first.encrypt(value);
    assertNotEquals(encrypted, first.encrypt(value));
    assertFalse(encrypted.contains(value));
    var restarted = new AccountDataProtection(settings(true, ""));
    assertEquals(value, restarted.decrypt(encrypted));
    assertEquals(first.fingerprint("phone", value), restarted.fingerprint("phone", value));
    assertNotEquals(first.fingerprint("phone", value), first.fingerprint("identity", value));
    Path key = directory.resolve("account.key");
    if (key.getFileSystem().supportedFileAttributeViews().contains("posix"))
      assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(key));
    byte[] original = Files.readAllBytes(key);
    restarted.encrypt(value);
    assertArrayEquals(original, Files.readAllBytes(key));
  }

  @Test void tamperingAndWrongKeyNeverDecrypt() {
    var first = new AccountDataProtection(settings(true, Base64.getEncoder().encodeToString(new byte[32])));
    String encrypted = first.encrypt("private identity value");
    byte[] encoded = Base64.getUrlDecoder().decode(encrypted.substring(3));
    encoded[encoded.length - 1] ^= 1;
    String tampered = "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
    assertThrows(Problem.class, () -> first.decrypt(tampered));
    byte[] other = new byte[32]; other[0] = 1;
    var second = new AccountDataProtection(settings(false, Base64.getEncoder().encodeToString(other)));
    assertThrows(Problem.class, () -> second.decrypt(encrypted));
  }

  @Test void productionMissingOrMalformedKeyFailsWithoutGeneratingLocalKey() {
    assertThrows(Problem.class, () -> new AccountDataProtection(settings(false, "")).encrypt("value"));
    assertThrows(Problem.class, () -> new AccountDataProtection(settings(false, "invalid")).encrypt("value"));
    assertFalse(Files.exists(directory.resolve("account.key")));
  }
}
