package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void controllersDependOnServicesAndPersistenceSqlStaysInMappers() throws Exception {
    Path root = Path.of("src/main/java/com/sujian/mall");
    Pattern sql =
        Pattern.compile(
            "\\\"\\s*(SELECT|UPDATE|INSERT|DELETE|CREATE|ALTER)\\s+", Pattern.CASE_INSENSITIVE);
    try (var files = Files.walk(root)) {
      for (var path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(path);
        String relative = root.relativize(path).toString().replace('\\', '/');
        assertFalse(source.contains("JdbcTemplate"), relative + " must use MyBatis mappers");
        if (!relative.startsWith("mapper/"))
          assertFalse(sql.matcher(source).find(), relative + " must not contain persistence SQL");
        if (relative.startsWith("controller/")) {
          assertFalse(source.contains("import com.sujian.mall.mapper."), relative);
          assertFalse(source.contains("import com.sujian.mall.integration."), relative);
        }
      }
    }
  }
}
