package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.n;
import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.identity.IdentityGateway;
import com.sujian.mall.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:identity;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "JOB_INITIAL_DELAY_MS=3600000"
})
class IdentityServiceTest {
  static class TestSettings extends Settings {
    volatile boolean development = true;
    volatile boolean required = true;
    @Override public boolean dev() { return development; }
    @Override public String get(String key, String fallback) {
      return switch (key) {
        case "APP_DEV_MODE" -> "true";
        case "ADMIN_BOOTSTRAP_PASSWORD" -> "identity-test-admin-password";
        case "ACCOUNT_DATA_KEY" -> "SWRlbnRpdHlUZXN0S2V5MDEyMzQ1Njc4OUFiQ2RFZmc=";
        case "IDENTITY_REQUIRED_FOR_WITHDRAWAL" -> Boolean.toString(required);
        case "IDENTITY_MAX_ATTEMPTS_PER_DAY" -> "2";
        case "IDENTITY_PROVIDER" -> "aliyun";
        default -> super.get(key, fallback);
      };
    }
  }

  static class Gateway implements IdentityGateway {
    final AtomicInteger calls = new AtomicInteger();
    volatile java.util.function.BiFunction<String, String, Verification> answer;
    @Override public Verification verify(String name, String id) {
      calls.incrementAndGet();
      return answer.apply(name, id);
    }
  }

  @TestConfiguration
  static class Config {
    @Bean @Primary TestSettings identityTestSettings() { return new TestSettings(); }
    @Bean @Primary Gateway identityTestGateway() { return new Gateway(); }
  }

  @Autowired IdentityService identities;
  @Autowired AuthService auth;
  @Autowired WithdrawalService withdrawals;
  @Autowired JdbcTemplate jdbc;
  @Autowired TestSettings settings;
  @Autowired Gateway gateway;
  private final String number = ResidentIdentityTest.number("11010519900101001");

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM identity_attempt");
    jdbc.update("DELETE FROM member_identity");
    settings.development = true;
    settings.required = true;
    gateway.calls.set(0);
    gateway.answer = (name, id) -> new IdentityGateway.Verification(true, UUID.randomUUID().toString(), "", false);
  }

  private long member() {
    var session = auth.devLogin(Map.of("name", "实名测试" + System.nanoTime(), "inviteCode", ""));
    return n((Map<String, Object>) session.get("profile"), "id");
  }

  private void age(long uid) {
    jdbc.update("UPDATE member_identity SET last_attempt_at=? WHERE member_id=?", System.currentTimeMillis()-180_000, uid);
  }

  @Test
  void validatesConsentAndIdentityBeforeUsingPaidProvider() {
    long uid = member();
    assertEquals("UNVERIFIED", identities.status(uid).get("status"));
    assertEquals(0L, identities.status(uid).get("retryAfter"));
    assertThrows(Problem.class, () -> identities.verify(uid, "测试人", number, false));
    assertThrows(Problem.class, () -> identities.verify(uid, "测试人", "123", true));
    assertEquals(0, gateway.calls.get());
  }

  @Test
  void encryptsSensitiveValuesAndDoesNotCallProviderAgainAfterVerification() {
    long uid = member();
    var result = identities.verify(uid, "测试人", number, true);
    assertEquals("VERIFIED", result.get("status"));
    assertEquals("测**", result.get("nameMasked"));
    assertFalse(result.toString().contains(number));
    assertFalse(result.toString().contains("测试人"));
    var stored = jdbc.queryForMap("SELECT * FROM member_identity WHERE member_id=?", uid);
    assertFalse(stored.get("name_ciphertext").toString().contains("测试人"));
    assertFalse(stored.get("id_number_ciphertext").toString().contains(number));
    assertNotNull(stored.get("consent_at"));
    assertEquals(result, identities.verify(uid, "测试人", number, true));
    assertThrows(Problem.class, () -> identities.verify(uid, "其他人", number, true));
    assertEquals(1, gateway.calls.get());
  }

  @Test
  void rejectsDuplicateVerifiedIdentityButAFailedNameDoesNotReserveIt() {
    long first = member(), second = member(), third = member();
    gateway.answer = (name,id) -> new IdentityGateway.Verification(false, "req-1", number+name, false);
    var rejected = identities.verify(first, "测试人", number, true);
    assertEquals("REJECTED", rejected.get("status"));
    assertFalse(rejected.toString().contains(number));
    gateway.answer = (name,id) -> new IdentityGateway.Verification(true, "req-2", "", false);
    assertEquals("VERIFIED", identities.verify(second, "测试人", number, true).get("status"));
    assertEquals("REJECTED", identities.verify(third, "测试人", number, true).get("status"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM member_identity WHERE state='VERIFIED'", Integer.class));
  }

  @Test
  void providerTimeoutPersistsUnknownAndOnlyAnExpiredLeaseAllowsExplicitRetry() {
    long uid = member();
    gateway.answer = (name,id) -> { throw new IllegalStateException(name+id); };
    var failure = assertThrows(Problem.class, () -> identities.verify(uid, "测试人", number, true));
    assertFalse(failure.getMessage().contains(number));
    assertEquals("PENDING", identities.status(uid).get("status"));
    assertTrue(((Number) identities.status(uid).get("retryAfter")).longValue() > 0);
    assertEquals("UNKNOWN", jdbc.queryForObject("SELECT state FROM identity_attempt WHERE member_id=?", String.class, uid));
    assertEquals("PENDING", identities.verify(uid, "测试人", number, true).get("status"));
    assertEquals(1, gateway.calls.get());
    age(uid);
    assertEquals(0L, identities.status(uid).get("retryAfter"));
    gateway.answer = (name,id) -> new IdentityGateway.Verification(true, "req-3", "", false);
    assertEquals("VERIFIED", identities.verify(uid, "测试人", number, true).get("status"));
    assertEquals(2, gateway.calls.get());
  }

  @Test
  void failedProviderCallsDoNotRollbackTheDailyPaidAttemptLimit() {
    long uid = member();
    gateway.answer = (name,id) -> { throw new IllegalStateException("channel unavailable"); };
    assertThrows(Problem.class, () -> identities.verify(uid, "测试人", number, true));
    age(uid);
    assertThrows(Problem.class, () -> identities.verify(uid, "测试人", number, true));
    age(uid);
    assertEquals("IDENTITY_DAILY_LIMIT", assertThrows(Problem.class,
        () -> identities.verify(uid, "测试人", number, true)).code);
    assertEquals(2, gateway.calls.get());
  }

  @Test
  void productionCannotTrustDevelopmentVerificationOrBypassRequiredIdentity() {
    long uid = member();
    gateway.answer = (name,id) -> new IdentityGateway.Verification(true, "dev", "", true);
    assertEquals(true, identities.verify(uid, "测试人", number, true).get("development"));
    settings.development = false;
    settings.required = false;
    assertEquals("REAL_IDENTITY_REQUIRED", assertThrows(Problem.class,
        () -> identities.requireVerifiedForWithdrawal(uid)).code);
  }

  @Test
  void productionNeedsRealWechatBindingEvenWhenNameAndIdMatched() {
    long uid = member();
    identities.verify(uid, "测试人", number, true);
    settings.development = false;
    assertEquals("WECHAT_BINDING_REQUIRED", assertThrows(Problem.class,
        () -> identities.requireVerifiedForWithdrawal(uid)).code);
    jdbc.update("UPDATE member SET openid=NULL WHERE id=?", uid);
    assertEquals("WECHAT_BINDING_REQUIRED", assertThrows(Problem.class,
        () -> identities.requireVerifiedForWithdrawal(uid)).code);
    jdbc.update("UPDATE member SET openid=? WHERE id=?", "actual-wechat-openid-123456", uid);
    assertDoesNotThrow(() -> identities.requireVerifiedForWithdrawal(uid));
  }

  @Test
  void bothWithdrawalApplicationAndExecutionCheckIdentityBeforeFundsMove() {
    long uid = member();
    assertEquals("IDENTITY_REQUIRED", assertThrows(Problem.class,
        () -> withdrawals.withdraw(uid, "identity-test-withdraw", Map.of("amountFen", 1000))).code);
    String withdrawal = "ID-WD-"+uid;
    jdbc.update("INSERT INTO withdrawal(id,member_id,gross,tax,net,state,reviewer,created_at) VALUES(?,?,?,?,?,?,?,?)",
        withdrawal,uid,1000,0,1000,"APPROVED",901,System.currentTimeMillis());
    assertEquals("IDENTITY_REQUIRED", assertThrows(Problem.class,
        () -> withdrawals.executeWithdrawal(902, withdrawal)).code);
    assertEquals("APPROVED", jdbc.queryForObject("SELECT state FROM withdrawal WHERE id=?", String.class, withdrawal));
    assertEquals(0L, jdbc.queryForObject("SELECT reserved FROM member WHERE id=?", Long.class, uid));
  }

  @Test
  void duplicateConcurrentSubmissionDoesNotDoubleChargeTheProvider() throws Exception {
    long uid = member();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    gateway.answer = (name,id) -> {
      entered.countDown();
      try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
      catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
      return new IdentityGateway.Verification(true,"req-concurrent","",false);
    };
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> identities.verify(uid,"测试人",number,true));
      assertTrue(entered.await(10,TimeUnit.SECONDS));
      var second = executor.submit(() -> identities.verify(uid,"测试人",number,true));
      assertEquals("PENDING", second.get(10,TimeUnit.SECONDS).get("status"));
      release.countDown();
      assertEquals("VERIFIED", first.get(10,TimeUnit.SECONDS).get("status"));
      assertEquals(1,gateway.calls.get());
    } finally { release.countDown(); }
  }

  @Test
  void latePositiveResponseCannotOverwriteANewerRejectedAttempt() throws Exception {
    long uid = member();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    gateway.answer = (name,id) -> {
      entered.countDown();
      try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
      catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
      return new IdentityGateway.Verification(true,"req-old","",false);
    };
    try (var executor = Executors.newSingleThreadExecutor()) {
      var old = executor.submit(() -> identities.verify(uid,"测试人",number,true));
      assertTrue(entered.await(10,TimeUnit.SECONDS));
      age(uid);
      gateway.answer = (name,id) -> new IdentityGateway.Verification(false,"req-new","",false);
      assertEquals("REJECTED", identities.verify(uid,"测试人",number,true).get("status"));
      release.countDown();
      assertEquals("REJECTED", old.get(10,TimeUnit.SECONDS).get("status"));
      assertEquals("REJECTED",identities.status(uid).get("status"));
    } finally { release.countDown(); }
  }
}
