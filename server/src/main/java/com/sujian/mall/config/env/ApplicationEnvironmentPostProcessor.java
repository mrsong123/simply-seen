package com.sujian.mall.config.env;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

/** Installs the repository dotenv before Boot decides profiles or binds its datasource. */
public final class ApplicationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  private static final Set<String> FILE_REFERENCES = Set.of("APP_CONFIG_FILE", "RESTORE_FILE");
  @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 9; }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    String configured = environment.getProperty("APP_CONFIG_FILE");
    Path file = locate(working, configured);
    Path base = file == null ? working : file.getParent();
    if (file != null) {
      Map<String, Object> values;
      try {
        if (!Files.isRegularFile(file) || !Files.isReadable(file) || Files.size(file) > 1048576)
          throw new IllegalStateException("APP_CONFIG_FILE配置文件不可读取");
        values = DotenvParser.parse(Files.readString(file, StandardCharsets.UTF_8));
      } catch (java.io.IOException error) {
        throw new IllegalStateException("APP_CONFIG_FILE配置文件不可读取");
      }
      var source = new SystemEnvironmentPropertySource("sujian-dotenv", values);
      if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
        environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
      else environment.getPropertySources().addLast(source);
    }
    var resolved = new LinkedHashMap<String, Object>();
    resolved.put("APP_CONFIG_BASE_DIR", base.toString());
    resolved.put("APP_CONFIG_FILES_RESOLVED", "true");
    Set<String> names = new LinkedHashSet<>();
    for (PropertySource<?> source : environment.getPropertySources())
      if (source instanceof EnumerablePropertySource<?> enumerable)
        for (String name : enumerable.getPropertyNames())
          if (name.matches("[A-Z][A-Z0-9_]*_FILE") && !FILE_REFERENCES.contains(name)
              && (owned(name) || environment.getPropertySources().get("sujian-dotenv") != null
                  && environment.getPropertySources().get("sujian-dotenv").getProperty(name) != null))
            names.add(name.substring(0, name.length() - 5));
    for (String name : names) {
      for (PropertySource<?> source : environment.getPropertySources()) {
        Object direct = source.getProperty(name), indirect = source.getProperty(name + "_FILE");
        if (direct != null && !direct.toString().isBlank()) break;
        if (indirect != null && !indirect.toString().isBlank()) {
          resolved.put(name, ConfigurationFiles.secret(base, indirect.toString(), name));
          break;
        }
        if (direct != null) { resolved.put(name, direct.toString()); break; }
      }
    }
    if (file != null) resolved.put("APP_CONFIG_LOADED_FILE", file.toString());
    environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("sujian-resolved-files", resolved));
    // The datasource is configured by Boot itself, before Settings exists.
    boolean embedded = !"external".equals(environment.getProperty("DATABASE_MODE", "embedded"));
    if (embedded) {
      derive(environment, resolved, "DATABASE_USER", "MYSQL_USER");
      derive(environment, resolved, "DATABASE_PASSWORD", "MYSQL_PASSWORD");
    }
    if (environment.getProperty("DATABASE_URL", "").isBlank()) {
      String database = environment.getProperty("MYSQL_DATABASE", "");
      if (embedded && database.matches("[A-Za-z0-9_]{1,64}"))
        resolved.put("DATABASE_URL", "jdbc:mysql://mysql:3306/" + database + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&sslMode=REQUIRED");
      else if (embedded && file != null)
        resolved.put("DATABASE_URL", "jdbc:h2:file:" + base.resolve("data/mall").toString().replace('\\', '/')
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE");
    }
  }

  private void derive(ConfigurableEnvironment environment, Map<String, Object> resolved, String name, String source) {
    String existing = environment.getProperty(name), fallback = environment.getProperty(source);
    if ((existing == null || existing.isBlank()) && fallback != null && !fallback.isBlank()) resolved.put(name, fallback);
  }

  private boolean owned(String name) {
    return name.equals("PORT_FILE") || name.matches("(APP|ACCOUNT|WX|SMS|IDENTITY|DATABASE|MYSQL|LOGISTICS|ADMIN|PUBLIC|SUPPORT|PRIVACY|SESSION|CORS|ORDER|JOB|MAINTENANCE|API|WITHDRAW|TRANSFER|MONTHLY|DISTRIBUTION)_.*");
  }

  private Path locate(Path working, String configured) {
    if (configured != null) return configured.isBlank() ? null : ConfigurationFiles.resolve(working, configured);
    Path current = working.resolve(".env");
    if (Files.isRegularFile(current)) return current;
    Path parent = working.getParent();
    if (parent != null && working.getFileName().toString().equals("server") && Files.isRegularFile(parent.resolve("pom.xml"))) {
      Path project = parent.resolve(".env");
      if (Files.isRegularFile(project)) return project;
    }
    return null;
  }
}
