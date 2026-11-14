package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.LocalCredentialsStore;
import com.sujian.mall.common.Problem;
import com.sujian.mall.config.ProductionConfigurationValidator;
import com.sujian.mall.config.Settings;
import com.sujian.mall.service.AuthService;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

class LocalStartupTest {
  @TempDir Path directory;

  @Test
  void freshIdeStartupCreatesPrivateCredentialsAndDoesNotResetThem() throws Exception {
    Path credentials = directory.resolve("bootstrap-admin.txt");
    String db =
        "jdbc:h2:mem:local-startup-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    String[] arguments = {
      "--spring.profiles.active=local",
      "--spring.datasource.url=" + db,
      "--spring.datasource.username=sa",
      "--spring.datasource.password=",
      "--APP_DEV_MODE=true",
      "--ADMIN_BOOTSTRAP_PASSWORD=",
      "--APP_BOOTSTRAP_CREDENTIALS_PATH=" + credentials,
      "--JOB_INITIAL_DELAY_MS=3600000",
      "--API_DOCS_ENABLED=false"
    };
    byte[] original;
    try (var context =
        new SpringApplicationBuilder(Application.class)
            .web(WebApplicationType.NONE)
            .run(arguments)) {
      assertTrue(context.getBean(Settings.class).dev());
      assertTrue(Files.isRegularFile(credentials));
      Properties values = new Properties();
      try (var reader = Files.newBufferedReader(credentials)) {
        values.load(reader);
      }
      assertTrue(values.getProperty("password").length() >= 24);
      var login =
          context
              .getBean(AuthService.class)
              .adminLogin(
                  Map.of(
                      "username",
                      values.getProperty("username"),
                      "password",
                      values.getProperty("password")),
                  "local-startup-test");
      assertNotNull(login.get("token"));
      original = Files.readAllBytes(credentials);
      if (credentials.getFileSystem().supportedFileAttributeViews().contains("posix"))
        assertEquals(
            PosixFilePermissions.fromString("rw-------"),
            Files.getPosixFilePermissions(credentials));
    }
    try (var context =
        new SpringApplicationBuilder(Application.class)
            .web(WebApplicationType.NONE)
            .run(arguments)) {
      assertArrayEquals(original, Files.readAllBytes(credentials));
      assertTrue(context.getBean(Settings.class).dev());
    }
  }

  @Test
  void productionProfileCannotEnableSimulationAndStillRequiresConfiguration() {
    var environment = new MockEnvironment().withProperty("APP_DEV_MODE", "true");
    environment.setActiveProfiles("prod");
    var settings = new Settings(environment);
    assertFalse(settings.dev());
    assertThrows(Problem.class, () -> new ProductionConfigurationValidator(settings).validate());
    assertThrows(Problem.class, () -> new LocalCredentialsStore().obtain(settings));
  }
}
