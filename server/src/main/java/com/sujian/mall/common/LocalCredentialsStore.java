package com.sujian.mall.common;

import com.sujian.mall.config.Settings;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Creates a local-only first-login credential file without writing secrets to logs. */
@Component
public class LocalCredentialsStore {
  public record Credentials(String username, String password) {}

  public Credentials obtain(Settings settings) {
    Problem.require(settings.dev(), "PRODUCTION_CREDENTIALS", "生产环境禁止自动生成管理员口令");
    Path path =
        Path.of(settings.get("APP_BOOTSTRAP_CREDENTIALS_PATH", "./data/bootstrap-admin.txt"))
            .toAbsolutePath()
            .normalize();
    try {
      if (Files.exists(path)) {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
          properties.load(reader);
        }
        String username = properties.getProperty("username", "");
        String password = properties.getProperty("password", "");
        Problem.require(
            !username.isBlank() && password.length() >= 12,
            "INVALID_LOCAL_CREDENTIALS",
            "本地凭据文件无效，请保留备份后检查日志中的文件路径");
        return new Credentials(username, password);
      }
      if (path.getParent() != null) Files.createDirectories(path.getParent());
      byte[] bytes = new byte[24];
      new SecureRandom().nextBytes(bytes);
      String username = settings.get("ADMIN_BOOTSTRAP_USER", "admin");
      String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      if (path.getFileSystem().supportedFileAttributeViews().contains("posix"))
        Files.createFile(
            path,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      else Files.createFile(path);
      Files.writeString(
          path, "username=" + username + "\npassword=" + password + "\n", StandardOpenOption.WRITE);
      LoggerFactory.getLogger(LocalCredentialsStore.class).info("本地初始管理员凭据文件：{}", path);
      return new Credentials(username, password);
    } catch (IOException e) {
      throw new IllegalStateException("无法创建或读取本地管理员凭据文件：" + path, e);
    }
  }
}
