package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.sms.SmsGateway;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.service.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class PhoneAuthenticationFixture {
  @TestConfiguration
  static class Config {
    @Bean @Primary PhoneTestSettings phoneTestSettings() { return new PhoneTestSettings(); }
    @Bean @Primary FakeSms fakeSms() { return new FakeSms(); }
    @Bean @Primary WechatClient phoneTestWechat(Settings settings, JsonCodec json) {
      return new WechatClient(settings, json) {
        @Override public Map<String, Object> login(String code) { return Map.of("openid", code); }
      };
    }
  }

  static class PhoneTestSettings extends Settings {
    volatile boolean development = true;
    @Override public boolean dev() { return development; }
    @Override public String get(String key, String fallback) {
      return switch (key) {
        case "APP_DEV_MODE" -> "true";
        case "ACCOUNT_DATA_KEY" -> Base64.getEncoder().encodeToString(new byte[32]);
        case "ADMIN_BOOTSTRAP_PASSWORD" -> "phone-auth-test-only-password";
        default -> super.get(key, fallback);
      };
    }
  }

  static class FakeSms implements SmsGateway {
    volatile boolean failure;
    final AtomicInteger calls = new AtomicInteger();
    public Delivery send(String phone, String code) {
      calls.incrementAndGet();
      if (failure) throw new Problem(503, "SMS_DELIVERY_FAILED", "短信服务测试失败");
      return new Delivery(UUID.randomUUID().toString(), true);
    }
  }

  @Autowired PhoneAuthService phones;
  @Autowired SmsChallengeService challenges;
  @Autowired AuthService auth;
  @Autowired AccountDataProtection protection;
  @Autowired PhoneTestSettings settings;
  @Autowired FakeSms gateway;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void clearPhoneFixtures() {
    settings.development = true;
    gateway.failure = false;
    gateway.calls.set(0);
    for (String table : List.of("member_phone", "sms_challenge", "sms_throttle", "sms_cooldown",
        "account_security_lock", "identity_attempt", "member_identity", "session_token", "member"))
      jdbc.update("DELETE FROM " + table);
  }

  Map<String, Object> issue(String phone) { return phones.sendLoginCode(phone, "phone-test-ip"); }

  Map<String, Object> login(String phone, Map<String, Object> challenge) {
    return phones.login(phone, s(challenge, "devCode"), s(challenge, "challengeId"), "", true, "phone-test-ip");
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> profile(Map<String, Object> session) {
    return (Map<String, Object>) session.get("profile");
  }

  long member(String name) {
    return n(profile(auth.devLogin(Map.of("name", name, "inviteCode", ""))), "id");
  }

  void allowResend() { jdbc.update("UPDATE sms_cooldown SET next_allowed_at=0"); }
}
