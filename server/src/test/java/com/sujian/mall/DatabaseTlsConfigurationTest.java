package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.config.DatabaseTlsConfiguration;
import com.sujian.mall.config.Settings;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class DatabaseTlsConfigurationTest {
  @TempDir Path directory;

  @Test
  void databaseCaDoesNotAlterH2OrAllowDisabledVerification() {
    var settings=new Settings(new MockEnvironment().withProperty("DATABASE_CA_PATH",directory.resolve("missing.pem").toString()));
    try(var source=new HikariDataSource()) {
      source.setJdbcUrl("jdbc:h2:mem:tls-config");
      assertDoesNotThrow(()->DatabaseTlsConfiguration.configure(source,settings));
      assertTrue(source.getDataSourceProperties().isEmpty());
      source.setJdbcUrl("jdbc:mysql://database.example/shop?sslMode=DISABLED");
      assertThrows(IllegalStateException.class,()->DatabaseTlsConfiguration.configure(source,settings));
      source.setJdbcUrl("jdbc:mysql://database.example/shop?sslMode=VERIFY_IDENTITY");
      assertThrows(IllegalStateException.class,()->DatabaseTlsConfiguration.configure(source,settings));
    }
  }

  @Test
  void validCaIsLoadedIntoDataSourceSpecificTrustWithoutChangingGlobalTls() throws Exception {
    Path store=directory.resolve("fixture.p12"),certificate=directory.resolve("fixture.pem");
    String keytool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase().contains("windows")?"keytool.exe":"keytool").toString();
    execute(keytool,"-genkeypair","-alias","database","-keyalg","RSA","-keysize","2048","-dname","CN=database.fixture.test","-validity","2","-storetype","PKCS12","-keystore",store.toString(),"-storepass","fixture-only-password","-keypass","fixture-only-password","-noprompt");
    execute(keytool,"-exportcert","-rfc","-alias","database","-keystore",store.toString(),"-storepass","fixture-only-password","-file",certificate.toString());
    String globalStore=System.getProperty("javax.net.ssl.trustStore");
    var settings=new Settings(new MockEnvironment().withProperty("DATABASE_CA_PATH",certificate.toString()));
    try(var source=new HikariDataSource()) {
      source.setJdbcUrl("jdbc:mysql://database.fixture.test/shop?sslMode=VERIFY_CA");
      DatabaseTlsConfiguration.configure(source,settings);
      var properties=source.getDataSourceProperties();
      assertEquals("VERIFY_CA",properties.getProperty("sslMode"));
      assertEquals("PKCS12",properties.getProperty("trustCertificateKeyStoreType"));
      Path generated=Path.of(URI.create(properties.getProperty("trustCertificateKeyStoreUrl")));
      try(var input=Files.newInputStream(generated)) {
        var trust=KeyStore.getInstance("PKCS12");
        trust.load(input,properties.getProperty("trustCertificateKeyStorePassword").toCharArray());
        assertEquals(1,trust.size());
      } finally {Files.deleteIfExists(generated);}
      assertEquals(globalStore,System.getProperty("javax.net.ssl.trustStore"));
    }
  }

  private void execute(String... command) throws Exception {
    var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    assertTrue(process.waitFor(30,TimeUnit.SECONDS),"Fixture keytool did not finish");
    assertEquals(0,process.exitValue(),"Fixture keytool failed");
  }
}
