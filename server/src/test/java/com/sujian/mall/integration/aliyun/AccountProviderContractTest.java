package com.sujian.mall.integration.aliyun;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujian.mall.common.JsonCodec;
import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.identity.ConfiguredIdentityGateway;
import com.sujian.mall.integration.sms.ConfiguredSmsGateway;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class AccountProviderContractTest {
  final JsonCodec json = new JsonCodec(new ObjectMapper());

  Settings settings(boolean development, Map<String, String> overrides) {
    var env = new MockEnvironment();
    env.setActiveProfiles(development ? "local" : "prod");
    env.withProperty("APP_DEV_MODE", "true");
    for (String prefix : new String[] {"SMS", "IDENTITY"}) {
      env.withProperty(prefix + "_API_URL", "https://provider.example.test/")
          .withProperty(prefix + "_ACCESS_KEY_ID", "unit-test-access")
          .withProperty(prefix + "_ACCESS_KEY_SECRET", "unit-test-secret")
          .withProperty(prefix + "_REGION_ID", "cn-hangzhou");
    }
    env.withProperty("SMS_SIGN_NAME", "素见测试")
        .withProperty("SMS_TEMPLATE_CODE", "SMS_TEST");
    overrides.forEach(env::withProperty);
    return new Settings(env);
  }

  AliyunRpcClient client(Settings settings, String response) {
    return new AliyunRpcClient(
        settings, json, (url, body) -> new AliyunRpcTransport.Response(200, response));
  }

  @Test
  void usesOfficialSmsFieldsAndAcceptsOnlyOkWithReceipt() {
    Settings config = settings(false, Map.of());
    var sent = new AtomicReference<Map<String, String>>();
    var rpc = new AliyunRpcClient(config, json, (uri, body) -> {
      sent.set(AliyunRpcClientTest.parse(body));
      return new AliyunRpcTransport.Response(200, "{\"Code\":\"OK\",\"RequestId\":\"sms-request-1\",\"BizId\":\"delivery-id\"}");
    });
    var delivery = new ConfiguredSmsGateway(config, json, rpc).send("13800138000", "123456");
    assertFalse(delivery.development());
    assertEquals("sms-request-1", delivery.requestId());
    assertEquals("SendSms", sent.get().get("Action"));
    assertEquals("2017-05-25", sent.get().get("Version"));
    assertEquals("13800138000", sent.get().get("PhoneNumbers"));
    assertEquals("SMS_TEST", sent.get().get("TemplateCode"));
    assertEquals(Map.of("code", "123456"), json.decode(sent.get().get("TemplateParam")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"Code\":\"OK\"}", "{\"Code\":\"ok\",\"RequestId\":\"id\"}", "{\"Code\":\"ERROR\",\"Message\":\"13800138000 123456 AK-secret\"}"})
  void missingReceiptOrNonOkSmsResponseFailsWithoutLeakingSupplierMessage(String response) {
    Settings config = settings(false, Map.of());
    var gateway = new ConfiguredSmsGateway(config, json, client(config, response));
    Problem problem = assertThrows(Problem.class, () -> gateway.send("13800138000", "123456"));
    assertFalse(problem.getMessage().contains("13800138000"));
    assertFalse(problem.getMessage().contains("123456"));
    assertFalse(problem.getMessage().contains("AK-secret"));
  }

  @Test
  void smsRateRejectionRemainsRateError() {
    Settings config = settings(false, Map.of());
    var gateway = new ConfiguredSmsGateway(config, json, client(config, "{\"Code\":\"isv.BUSINESS_LIMIT_CONTROL\"}"));
    assertEquals(429, assertThrows(Problem.class, () -> gateway.send("13800138000", "123456")).status);
  }

  @Test
  void identityMatchRequiresBothOfficialPositiveCodesAndUsesCorrectInputFields() {
    Settings config = settings(false, Map.of());
    var sent = new AtomicReference<Map<String, String>>();
    var rpc = new AliyunRpcClient(config, json, (uri, body) -> {
      sent.set(AliyunRpcClientTest.parse(body));
      return new AliyunRpcTransport.Response(200, "{\"Code\":\"200\",\"RequestId\":\"identity-request\",\"ResultObject\":{\"BizCode\":\"1\"}}");
    });
    var outcome = new ConfiguredIdentityGateway(config, rpc).verify("示例姓名", "11010519491231002X");
    assertTrue(outcome.matched());
    assertFalse(outcome.development());
    assertEquals("Id2MetaVerify", sent.get().get("Action"));
    assertEquals("2019-03-07", sent.get().get("Version"));
    assertEquals("示例姓名", sent.get().get("UserName"));
    assertEquals("11010519491231002X", sent.get().get("IdentifyNum"));
    assertEquals("normal", sent.get().get("ParamType"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2", "3"})
  void explicitIdentityNonMatchNeverPassesOrEchoesSupplierMessage(String businessCode) {
    Settings config = settings(false, Map.of());
    String body = "{\"Code\":\"200\",\"RequestId\":\"request-2\",\"Message\":\"echo-name-and-id\",\"ResultObject\":{\"BizCode\":\"" + businessCode + "\"}}";
    var outcome = new ConfiguredIdentityGateway(config, client(config, body)).verify("示例姓名", "11010519491231002X");
    assertFalse(outcome.matched());
    assertFalse(outcome.development());
    assertFalse(outcome.reason().contains("echo-name-and-id"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{}",
    "{\"Code\":\"200\",\"RequestId\":\"r\"}",
    "{\"Code\":\"200\",\"ResultObject\":{\"BizCode\":\"1\"}}",
    "{\"Code\":\"200\",\"RequestId\":\"r\",\"ResultObject\":{\"BizCode\":\"0\"}}",
    "{\"Code\":\"500\",\"RequestId\":\"r\",\"Message\":\"echo-name-and-id\",\"ResultObject\":{\"BizCode\":\"1\"}}"
  })
  void incompleteOrUnknownIdentityResponsesFailClosed(String response) {
    Settings config = settings(false, Map.of());
    var gateway = new ConfiguredIdentityGateway(config, client(config, response));
    Problem problem = assertThrows(Problem.class, () -> gateway.verify("示例姓名", "11010519491231002X"));
    assertFalse(problem.getMessage().contains("echo-name-and-id"));
  }

  @Test
  void developmentIsExplicitAndCannotBeEnabledByProdAppDevFlag() {
    Settings local = settings(true, Map.of());
    var localRpc = client(local, "{}");
    assertTrue(new ConfiguredSmsGateway(local, json, localRpc).send("13800138000", "123456").development());
    assertTrue(new ConfiguredIdentityGateway(local, localRpc).verify("示例姓名", "11010519491231002X").development());
    Settings prod = settings(false, Map.of("SMS_PROVIDER", "development", "IDENTITY_PROVIDER", "development"));
    var prodRpc = client(prod, "{}");
    assertFalse(prod.dev());
    assertThrows(Problem.class, () -> new ConfiguredSmsGateway(prod, json, prodRpc).send("13800138000", "123456"));
    assertThrows(Problem.class, () -> new ConfiguredIdentityGateway(prod, prodRpc).verify("示例姓名", "11010519491231002X"));
  }

  @Test
  void unsupportedProvidersFailInsteadOfFallingBackToDevelopment() {
    Settings config = settings(true, Map.of("SMS_PROVIDER", "unknown", "IDENTITY_PROVIDER", "unknown"));
    assertThrows(Problem.class, () -> new ConfiguredSmsGateway(config, json, client(config, "{}")).send("13800138000", "123456"));
    assertThrows(Problem.class, () -> new ConfiguredIdentityGateway(config, client(config, "{}")).verify("示例姓名", "11010519491231002X"));
  }
}
