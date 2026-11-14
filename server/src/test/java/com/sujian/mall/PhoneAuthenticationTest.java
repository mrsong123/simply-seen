package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.Problem;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:phone-auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "JOB_INITIAL_DELAY_MS=3600000"
})
@Import(PhoneAuthenticationFixture.Config.class)
class PhoneAuthenticationTest extends PhoneAuthenticationFixture {
  @Test void firstLoginRegistersEncryptedPhoneAndReturnsOnlyMaskedAccountFields() {
    String phone = "13800138000";
    var challenge = issue(phone);
    var session = login(phone, challenge);
    var member = profile(session);
    assertNotNull(session.get("token"));
    assertEquals(true, member.get("phoneBound"));
    assertEquals("138****8000", member.get("phoneMasked"));
    assertEquals(false, member.get("wechatBound"));
    assertEquals("UNVERIFIED", member.get("identityStatus"));
    assertFalse(member.toString().contains(phone));
    var stored = jdbc.queryForMap("SELECT * FROM member_phone WHERE member_id=?", n(member, "id"));
    assertEquals(phone, protection.decrypt(stored.get("phone_ciphertext").toString()));
    assertNotEquals(phone, stored.get("phone_hash"));
    assertFalse(stored.get("phone_ciphertext").toString().contains(phone));
    assertEquals("", jdbc.queryForObject("SELECT code_hash FROM sms_challenge WHERE id=?", String.class,
        challenge.get("challengeId")));
  }

  @Test void loginRequiresConsentAndVerificationIsSingleUse() {
    String phone = "13800138000";
    var challenge = issue(phone);
    var rejected = assertThrows(Problem.class, () -> phones.login(phone, s(challenge, "devCode"),
        s(challenge, "challengeId"), "", false, "phone-test-ip"));
    assertEquals("AGREEMENT_REQUIRED", rejected.code);
    login(phone, challenge);
    assertEquals("SMS_CODE_USED", assertThrows(Problem.class, () -> login(phone, challenge)).code);
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM member_phone", Integer.class));
  }

  @Test void wrongAttemptsCommitAndFifthAttemptLocksEvenWhenLoginThrows() {
    String phone = "13800138000";
    var challenge = issue(phone);
    String wrong = s(challenge, "devCode").equals("000000") ? "000001" : "000000";
    for (int i = 1; i <= 5; i++) {
      var problem = assertThrows(Problem.class, () -> phones.login(phone, wrong,
          s(challenge, "challengeId"), "", true, "phone-test-ip"));
      assertEquals(i == 5 ? "SMS_CODE_LOCKED" : "SMS_CODE_INVALID", problem.code);
      assertEquals(i, jdbc.queryForObject("SELECT attempts FROM sms_challenge WHERE id=?", Integer.class,
          challenge.get("challengeId")));
    }
    assertEquals("SMS_CODE_LOCKED", assertThrows(Problem.class, () -> login(phone, challenge)).code);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member_phone", Integer.class));
  }

  @Test void expiredAndSupersededChallengesCannotLogin() {
    String phone = "13800138000";
    var first = issue(phone);
    jdbc.update("UPDATE sms_challenge SET expires_at=0 WHERE id=?", first.get("challengeId"));
    assertEquals("SMS_CODE_EXPIRED", assertThrows(Problem.class, () -> login(phone, first)).code);
    allowResend();
    var second = issue(phone);
    allowResend();
    var newest = issue(phone);
    assertEquals("SMS_CODE_SUPERSEDED", assertThrows(Problem.class, () -> login(phone, second)).code);
    login(phone, newest);
  }

  @Test void bindingProofIsScopedToPurposeAndMemberAndPreservesReferral() {
    long inviter = member("邀请人甲");
    auth.promote(inviter);
    String inviteCode = s(auth.profile(inviter), "invite_code");
    long child = n(profile(auth.devLogin(Map.of("name", "邀请人乙", "inviteCode", inviteCode))), "id");
    long other = member("其他会员");
    String phone = "13800138000";
    var proof = phones.sendBindingCode(child, phone, "binding-ip");
    assertEquals("SMS_CODE_INVALID", assertThrows(Problem.class, () -> login(phone, proof)).code);
    assertEquals("SMS_CODE_INVALID", assertThrows(Problem.class, () -> phones.bindPhone(other, phone,
        s(proof, "devCode"), s(proof, "challengeId"), "binding-ip")).code);
    var bound = phones.bindPhone(child, phone, s(proof, "devCode"), s(proof, "challengeId"), "binding-ip");
    assertEquals(child, n(bound, "id"));
    assertEquals(inviter, n(bound, "inviter_id"));
    allowResend();
    assertEquals(child, n(profile(login(phone, issue(phone))), "id"));
  }

  @Test void occupiedPhoneIsNotDisclosedBeforeProofAndAccountsAreNeverMerged() {
    String phone = "13800138000";
    long owner = n(profile(login(phone, issue(phone))), "id");
    long other = member("另一个会员");
    allowResend();
    var proof = phones.sendBindingCode(other, phone, "binding-ip");
    assertEquals("PHONE_IN_USE", assertThrows(Problem.class, () -> phones.bindPhone(other, phone,
        s(proof, "devCode"), s(proof, "challengeId"), "binding-ip")).code);
    assertEquals("SMS_CODE_USED", assertThrows(Problem.class, () -> phones.bindPhone(other, phone,
        s(proof, "devCode"), s(proof, "challengeId"), "binding-ip")).code);
    assertEquals(owner, jdbc.queryForObject("SELECT member_id FROM member_phone", Long.class));
    assertEquals("PHONE_ALREADY_BOUND", assertThrows(Problem.class,
        () -> phones.sendBindingCode(owner, "13900139000", "binding-ip")).code);
    assertEquals(false, auth.profile(other).get("phoneBound"));
  }

  @Test void disabledMemberCannotAcquireAnotherSession() {
    String phone = "13800138000";
    long id = n(profile(login(phone, issue(phone))), "id");
    jdbc.update("UPDATE member SET blocked=TRUE WHERE id=?", id);
    allowResend();
    var proof = issue(phone);
    assertEquals("ACCOUNT_DISABLED", assertThrows(Problem.class, () -> login(phone, proof)).code);
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM session_token WHERE actor_id=?", Integer.class, id));
    assertEquals("CONSUMED", jdbc.queryForObject("SELECT state FROM sms_challenge WHERE id=?", String.class,
        proof.get("challengeId")));
  }

  @Test void developmentChallengeCannotBeUsedAfterProductionSwitch() {
    String phone = "13800138000";
    var proof = issue(phone);
    settings.development = false;
    try {
      assertEquals("SMS_CODE_DEVELOPMENT", assertThrows(Problem.class, () -> login(phone, proof)).code);
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member_phone", Integer.class));
    } finally { settings.development = true; }
  }

  @Test void deliveryFailureInvalidatesCodeAndDoesNotRollBackCooldown() {
    gateway.failure = true;
    assertEquals("SMS_DELIVERY_FAILED", assertThrows(Problem.class, () -> issue("13800138000")).code);
    assertEquals("FAILED", jdbc.queryForObject("SELECT state FROM sms_challenge", String.class));
    assertEquals("", jdbc.queryForObject("SELECT code_hash FROM sms_challenge", String.class));
    gateway.failure = false;
    assertEquals("SMS_COOLDOWN", assertThrows(Problem.class, () -> issue("13800138000")).code);
  }

  @Test void ipBudgetCannotBeBypassedBySendingDifferentPhoneNumbers() {
    issue("13800138000"); issue("13900139000"); issue("13700137000");
    assertEquals("SMS_RATE_LIMIT", assertThrows(Problem.class, () -> issue("13600136000")).code);
    assertEquals(3, gateway.calls.get());
  }

  @Test void concurrentConsumptionHasExactlyOneWinner() throws Exception {
    String phone = "13800138000";
    var proof = issue(phone);
    var successes = new AtomicInteger();
    var unexpected = new ConcurrentLinkedQueue<Throwable>();
    try (var executor = Executors.newFixedThreadPool(12)) {
      var ready = new CountDownLatch(12);
      var start = new CountDownLatch(1);
      var futures = new ArrayList<Future<?>>();
      for (int i = 0; i < 12; i++) futures.add(executor.submit(() -> {
        ready.countDown();
        try {
          start.await();
          challenges.consume(phone, "LOGIN", 0, s(proof, "challengeId"), s(proof, "devCode"), "parallel-ip");
          successes.incrementAndGet();
        } catch (Problem failure) {
          if (!failure.code.equals("SMS_CODE_USED")) unexpected.add(failure);
        } catch (Throwable failure) { unexpected.add(failure); }
      }));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      for (var future : futures) future.get(20, TimeUnit.SECONDS);
    }
    assertTrue(unexpected.isEmpty(), unexpected.toString());
    assertEquals(1, successes.get());
  }

  @Test void wechatBindingPreservesAccountAndRejectsOccupiedOrDevelopmentIdentity() {
    long first = n(profile(login("13800138000", issue("13800138000"))), "id");
    long second = member("另一个微信会员");
    assertEquals(true, phones.bindWechat(first, "wechat-openid-one", "wx-binding-ip").get("wechatBound"));
    assertEquals(first, n(phones.bindWechat(first, "wechat-openid-one", "wx-binding-ip"), "id"));
    assertEquals("WECHAT_IN_USE", assertThrows(Problem.class,
        () -> phones.bindWechat(second, "wechat-openid-one", "wx-binding-ip")).code);
    assertEquals("WX_LOGIN_FAILED", assertThrows(Problem.class,
        () -> phones.bindWechat(second, "dev:forbidden", "wx-binding-ip")).code);
    assertEquals("WECHAT_ALREADY_BOUND", assertThrows(Problem.class,
        () -> phones.bindWechat(first, "wechat-openid-two", "wx-binding-ip")).code);
    assertEquals(first, jdbc.queryForObject("SELECT member_id FROM member_phone", Long.class));
  }
}
