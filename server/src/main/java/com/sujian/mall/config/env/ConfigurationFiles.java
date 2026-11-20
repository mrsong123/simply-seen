package com.sujian.mall.config.env;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class ConfigurationFiles {
  private ConfigurationFiles() {}
  public static Path resolve(Path base, String value) {
    if (value.startsWith("~/") || value.startsWith("~\\"))
      value = Path.of(System.getProperty("user.home")).resolve(value.substring(2)).toString();
    Path path = Path.of(value);
    return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
  }

  public static String secret(Path base, String value, String name) {
    Path file = resolve(base, value);
    try {
      if (!Files.isRegularFile(file) || !Files.isReadable(file) || Files.size(file) > 65536)
        throw new IllegalStateException("密钥文件不可读取：" + name + "_FILE");
      String contents = Files.readString(file, StandardCharsets.UTF_8);
      if (contents.endsWith("\r\n")) contents = contents.substring(0, contents.length() - 2);
      else if (contents.endsWith("\n")) contents = contents.substring(0, contents.length() - 1);
      if (contents.isEmpty() || contents.indexOf('\0') >= 0)
        throw new IllegalStateException("密钥文件内容无效：" + name + "_FILE");
      return contents;
    } catch (java.io.IOException error) {
      throw new IllegalStateException("密钥文件不可读取：" + name + "_FILE");
    }
  }
}
