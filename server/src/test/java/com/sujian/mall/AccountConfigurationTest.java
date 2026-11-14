package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.Problem;
import com.sujian.mall.config.AccountConfigurationValidator;
import com.sujian.mall.config.Settings;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AccountConfigurationTest {
  private Map<String, String> valid() {
    var values = new HashMap<String, String>();
    for (String prefix : List.of("SMS", "IDENTITY")) {
      values.put(prefix + "_PROVIDER", "aliyun");
      values.put(prefix + "_API_URL", "https://provider.example/");
      values.put(prefix + "_ACCESS_KEY_ID", "unit-test-access-id");
      values.put(prefix + "_ACCESS_KEY_SECRET", "unit-test-access-secret");
      values.put(prefix + "_REGION_ID", "cn-hangzhou");
    }
    values.put("SMS_SIGN_NAME", "unit-test-sign");
    values.put("SMS_TEMPLATE_CODE", "unit-test-template");
    values.put("ACCOUNT_DATA_KEY", Base64.getEncoder().encodeToString(new byte[32]));
    return values;
  }

  private Settings settings(Map<String, String> values) {
    return new Settings() {
      @Override
      public String get(String key, String fallback) {
        return values.getOrDefault(key, fallback);
      }
    };
  }

  @Test
  void productionRequiresRealProvidersCredentialsAndEncryptionKey() {
    var values = valid();
    assertDoesNotThrow(() -> AccountConfigurationValidator.validate(settings(values)));
    for (String key : List.of("ACCOUNT_DATA_KEY", "SMS_ACCESS_KEY_SECRET", "IDENTITY_ACCESS_KEY_SECRET")) {
      var missing = new HashMap<>(values);
      missing.remove(key);
      assertThrows(Problem.class, () -> AccountConfigurationValidator.validate(settings(missing)));
    }
    for (String key : List.of("SMS_PROVIDER", "IDENTITY_PROVIDER")) {
      var simulated = new HashMap<>(values);
      simulated.put(key, "development");
      assertThrows(Problem.class, () -> AccountConfigurationValidator.validate(settings(simulated)));
    }
  }

  @Test
  void endpointsAndMalformedKeyFailClosed() {
    for (String endpoint : List.of("http://provider.example/", "https://user@provider.example/", "https://provider.example/?secret=bad", "https://provider.example/#fragment")) {
      var values = valid();
      values.put("IDENTITY_API_URL", endpoint);
      assertThrows(Problem.class, () -> AccountConfigurationValidator.validate(settings(values)));
    }
    for (String key : List.of("not-base64", Base64.getEncoder().encodeToString(new byte[16]))) {
      var values = valid();
      values.put("ACCOUNT_DATA_KEY", key);
      assertThrows(Problem.class, () -> AccountConfigurationValidator.validate(settings(values)));
    }
  }

  @Test
  void productionCannotDisableWithdrawalIdentityRequirement() {
    var environment = new MockEnvironment()
        .withProperty("APP_DEV_MODE", "true")
        .withProperty("IDENTITY_REQUIRED_FOR_WITHDRAWAL", "false");
    environment.setActiveProfiles("prod");
    var settings = new Settings(environment);
    assertFalse(settings.dev());
    assertTrue(settings.identityRequiredForWithdrawal());
    assertEquals(true, settings.publicConfig().get("identityRequiredForWithdrawal"));
    assertEquals(true, settings.publicConfig().get("phoneLoginEnabled"));
    assertFalse(settings.publicConfig().containsKey("ACCOUNT_DATA_KEY"));
  }
}
