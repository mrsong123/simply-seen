package com.sujian.mall.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** A configured database CA affects only MySQL, without replacing HTTPS trust for other channels. */
@Configuration(proxyBeanMethods=false)
public class DatabaseTlsConfiguration {
  @Bean
  static BeanPostProcessor databaseTls(Environment environment) {
    var settings = new Settings(environment);
    return new BeanPostProcessor() {
      @Override public Object postProcessBeforeInitialization(Object bean, String name) {
        if (bean instanceof HikariDataSource source) configure(source,settings);
        return bean;
      }
    };
  }

  public static void configure(HikariDataSource source, Settings settings) {
    String configured = settings.get("DATABASE_CA_PATH", "");
    if (configured.isBlank() || source.getJdbcUrl()==null || !source.getJdbcUrl().startsWith("jdbc:mysql:")) return;
    var modeParameter=java.util.regex.Pattern.compile("[?&]sslMode=([^&;]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(source.getJdbcUrl());
    String mode=modeParameter.find()?modeParameter.group(1).toUpperCase(Locale.ROOT):"VERIFY_IDENTITY";
    if (!Set.of("VERIFY_CA","VERIFY_IDENTITY").contains(mode))
      throw new IllegalStateException("DATABASE_CA_PATH要求数据库sslMode为VERIFY_CA或VERIFY_IDENTITY");
    try {
      Path file=Path.of(configured);
      if (!Files.isRegularFile(file) || Files.size(file)>65536) throw new IllegalArgumentException();
      Collection<? extends java.security.cert.Certificate> certificates;
      try(InputStream input=Files.newInputStream(file)) {
        certificates=CertificateFactory.getInstance("X.509").generateCertificates(input);
      }
      if (certificates.isEmpty()) throw new IllegalArgumentException();
      KeyStore store=KeyStore.getInstance("PKCS12");
      byte[] random=new byte[24];new SecureRandom().nextBytes(random);
      char[] password=Base64.getEncoder().encodeToString(random).toCharArray();
      store.load(null,password);
      int index=0;
      for(var certificate:certificates) {
        ((X509Certificate)certificate).checkValidity();
        store.setCertificateEntry("database-ca-"+(index++),certificate);
      }
      Path temporary=Files.createTempFile("sujian-database-trust-", ".p12");
      if (temporary.getFileSystem().supportedFileAttributeViews().contains("posix"))
        Files.setPosixFilePermissions(temporary,PosixFilePermissions.fromString("rw-------"));
      temporary.toFile().deleteOnExit();
      try(OutputStream output=Files.newOutputStream(temporary)) {store.store(output,password);}
      var properties=source.getDataSourceProperties();
      properties.setProperty("trustCertificateKeyStoreUrl",temporary.toUri().toString());
      properties.setProperty("trustCertificateKeyStoreType","PKCS12");
      properties.setProperty("trustCertificateKeyStorePassword",new String(password));
      properties.setProperty("fallbackToSystemTrustStore","false");
      properties.setProperty("sslMode",mode);
      Arrays.fill(password,'\0');
    } catch(Exception error) {
      throw new IllegalStateException("DATABASE_CA_PATH必须为可读取且有效期内的X509证书PEM或证书链");
    }
  }
}
