package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.config.Settings;
import com.sujian.mall.config.env.ApplicationEnvironmentPostProcessor;
import com.sujian.mall.config.env.DotenvParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.*;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentConfigurationTest {
  @TempDir Path directory;

  private MockEnvironment environment(Map<String,Object> values) {
    var result=new MockEnvironment();
    result.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, values));
    return result;
  }
  private void process(MockEnvironment environment) {
    new ApplicationEnvironmentPostProcessor().postProcessEnvironment(environment,new SpringApplication(Application.class));
  }

  @Test
  void parserPreservesDataAndNeverEvaluatesShellText() {
    var parsed=DotenvParser.parse("\uFEFFexport SMS_SIGN_NAME=素见好物\r\n"
        +"DATABASE_URL=jdbc:mysql://db/sujian?a=1&b=2\r\n"
        +"WX_TRANSFER_REPORT_INFOS='[{\"info_type\":\"岗位类型\", \"info_content\":\"推广 # 服务\"}]'\r\n"
        +"VALUE=$(touch should-not-exist) `echo untouched`\r\n"
        +"QUOTED=\"a # b\\nsecond line\" # only comment\r\n");
    assertEquals("素见好物",parsed.get("SMS_SIGN_NAME"));
    assertEquals("jdbc:mysql://db/sujian?a=1&b=2",parsed.get("DATABASE_URL"));
    assertEquals("[{\"info_type\":\"岗位类型\", \"info_content\":\"推广 # 服务\"}]",parsed.get("WX_TRANSFER_REPORT_INFOS"));
    assertEquals("$(touch should-not-exist) `echo untouched`",parsed.get("VALUE"));
    assertEquals("a # b\nsecond line",parsed.get("QUOTED"));
  }

  @Test
  void malformedConfigurationErrorsNeverEchoPrivateContents() {
    String sensitive="do-not-print-this-secret";
    var error=assertThrows(IllegalStateException.class,()->DotenvParser.parse("BAD KEY="+sensitive));
    assertFalse(error.getMessage().contains(sensitive));
    assertThrows(IllegalStateException.class,()->DotenvParser.parse("SECRET=\"unclosed"));
    assertThrows(IllegalStateException.class,()->DotenvParser.parse("A=good\u0000bad"));
  }

  @Test
  void explicitFileSelectsProfilesBeforeBootAndResolvesPathsFromItsDirectory() throws Exception {
    Path file=directory.resolve("deployment.env");
    Files.writeString(file,"SPRING_PROFILES_ACTIVE=prod\nAPP_DEV_MODE=true\nWX_PRIVATE_KEY_PATH=keys/merchant.pem\nPUBLIC_STORE_URL=https://store.example.com/\n");
    var environment=environment(Map.of("APP_CONFIG_FILE",file.toString()));
    process(environment);
    assertArrayEquals(new String[]{"prod"},environment.getActiveProfiles());
    var settings=new Settings(environment);
    assertFalse(settings.dev());
    assertEquals(directory.resolve("keys/merchant.pem").toString(),settings.get("WX_PRIVATE_KEY_PATH",""));
    assertEquals("https://store.example.com/api/v1/callbacks/wechat/pay",settings.get("WX_PAY_NOTIFY_URL",""));
  }

  @Test
  void processAndCommandLineValuesOverrideDotenvAndFileSecrets() throws Exception {
    Files.writeString(directory.resolve("sms.secret"),"from-file\r\n");
    Path file=directory.resolve("deployment.env");
    Files.writeString(file,"SMS_ACCESS_KEY_SECRET=from-dotenv\nIDENTITY_ACCESS_KEY_SECRET_FILE=sms.secret\n");
    var environment=environment(Map.of("APP_CONFIG_FILE",file.toString(),"SMS_ACCESS_KEY_SECRET_FILE","sms.secret"));
    environment.getPropertySources().addFirst(new MapPropertySource("commandLineArgs",Map.of("SMS_ACCESS_KEY_SECRET","from-command")));
    process(environment);
    assertEquals("from-command",environment.getProperty("SMS_ACCESS_KEY_SECRET"));
    assertEquals("from-file",environment.getProperty("IDENTITY_ACCESS_KEY_SECRET"));
    var second=environment(Map.of("APP_CONFIG_FILE",file.toString(),"SMS_ACCESS_KEY_SECRET_FILE","sms.secret"));
    process(second);
    assertEquals("from-file",second.getProperty("SMS_ACCESS_KEY_SECRET"));
  }

  @Test
  void explicitEmptyValueDoesNotFallBackToALowerPrioritySecretFile() throws Exception {
    Files.writeString(directory.resolve("secret.txt"),"lower-priority");
    Path file=directory.resolve("deployment.env");
    Files.writeString(file,"SMS_ACCESS_KEY_SECRET_FILE=secret.txt\n");
    var environment=environment(Map.of("APP_CONFIG_FILE",file.toString(),"SMS_ACCESS_KEY_SECRET",""));
    process(environment);
    assertEquals("",new Settings(environment).get("SMS_ACCESS_KEY_SECRET","fallback"));
  }

  @Test
  void secretReaderPreservesSpacesAndRestoreFilenameIsNotSecretIndirection() throws Exception {
    Files.writeString(directory.resolve("secret.txt")," value with spaces \r\n");
    Path file=directory.resolve("deployment.env");
    Files.writeString(file,"SMS_ACCESS_KEY_SECRET_FILE=secret.txt\nRESTORE_FILE=backups/archive.sql.gz\n");
    var environment=environment(Map.of("APP_CONFIG_FILE",file.toString()));
    process(environment);
    assertEquals(" value with spaces ",environment.getProperty("SMS_ACCESS_KEY_SECRET"));
    assertEquals("backups/archive.sql.gz",environment.getProperty("RESTORE_FILE"));
    assertNull(environment.getProperty("RESTORE"));
  }

  @Test
  void explicitEmptyConfigDisablesDiscoveryAndMissingExplicitFilesFail() {
    var environment=environment(Map.of("APP_CONFIG_FILE",""));
    process(environment);
    assertNull(environment.getProperty("APP_CONFIG_LOADED_FILE"));
    assertTrue(environment.getProperty("APP_CONFIG_FILES_RESOLVED",Boolean.class,false));
    var missing=environment(Map.of("APP_CONFIG_FILE",directory.resolve("missing.env").toString()));
    assertThrows(IllegalStateException.class,()->process(missing));
  }

  @Test
  void processOnlyAccountAndApplicationFileVariablesResolveWithoutDotenv() throws Exception {
    Path account=directory.resolve("account.secret");
    Files.writeString(account,"AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=\r\n");
    Path support=directory.resolve("support.txt");
    Files.writeString(support,"素见客服\n");
    Path port=directory.resolve("port.txt");
    Files.writeString(port,"9090\n");
    var environment=environment(Map.of(
        "APP_CONFIG_FILE","",
        "ACCOUNT_DATA_KEY_FILE",account.toString(),
        "SUPPORT_CONTACT_FILE",support.toString(),
        "PRIVACY_CONTACT_FILE",support.toString(),
        "PORT_FILE",port.toString(),
        "AWS_SHARED_CREDENTIALS_FILE",directory.resolve("must-not-read").toString()));
    process(environment);
    var settings=new Settings(environment);
    assertEquals("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",settings.get("ACCOUNT_DATA_KEY",""));
    assertEquals("素见客服",settings.get("SUPPORT_CONTACT",""));
    assertEquals("素见客服",settings.get("PRIVACY_CONTACT",""));
    assertEquals("9090",environment.getProperty("PORT"));
    assertNull(environment.getProperty("AWS_SHARED_CREDENTIALS"));
    assertNull(environment.getProperty("APP_CONFIG_LOADED_FILE"));
  }

  @Test
  void localDatabaseAndMysqlCredentialsHaveConsistentDerivedSources() throws Exception {
    Path file=directory.resolve("local.env");
    Files.writeString(file,"APP_DEV_MODE=true\n");
    var local=environment(Map.of("APP_CONFIG_FILE",file.toString()));
    process(local);
    assertTrue(local.getProperty("DATABASE_URL").startsWith("jdbc:h2:file:"+directory.resolve("data/mall").toString().replace('\\','/')));
    Files.writeString(file,"MYSQL_DATABASE=sujian\nMYSQL_USER=merchant\nMYSQL_PASSWORD_FILE=db.secret\n");
    Files.writeString(directory.resolve("db.secret"),"database-secret\n");
    var mysql=environment(Map.of("APP_CONFIG_FILE",file.toString()));
    process(mysql);
    assertEquals("merchant",mysql.getProperty("DATABASE_USER"));
    assertEquals("database-secret",mysql.getProperty("DATABASE_PASSWORD"));
    assertTrue(mysql.getProperty("DATABASE_URL").startsWith("jdbc:mysql://mysql:3306/sujian?"));
  }
}
