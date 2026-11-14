package com.sujian.mall.service.impl;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.entity.SmsChallenge;
import com.sujian.mall.integration.sms.SmsGateway;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SmsChallengeServiceImpl implements SmsChallengeService {
  private static final int TTL_SECONDS = 300;
  private static final int MAX_ATTEMPTS = 5;
  private static final SecureRandom RANDOM = new SecureRandom();
  private final SmsChallengeMapper mapper;
  private final AccountSecurityLockMapper locks;
  private final AccountDataProtection protection;
  private final SmsThrottleService throttle;
  private final SmsGateway gateway;
  private final Settings settings;
  private final TransactionTemplate transaction;

  public SmsChallengeServiceImpl(SmsChallengeMapper mapper, AccountSecurityLockMapper locks,
      AccountDataProtection protection, SmsThrottleService throttle, SmsGateway gateway,
      Settings settings, PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.locks = locks;
    this.protection = protection;
    this.throttle = throttle;
    this.gateway = gateway;
    this.settings = settings;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Map<String, Object> send(String rawPhone, String purpose, long memberId, String ip) {
    String phone = PhoneNumbers.normalize(rawPhone);
    requirePurpose(purpose, memberId);
    String phoneHash = protection.fingerprint("phone", phone);
    throttle.reserve(phoneHash, ip);
    String id = UUID.randomUUID().toString().replace("-", "");
    String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    long now = System.currentTimeMillis();
    transaction.executeWithoutResult(status -> {
      String key = protection.fingerprint("sms-challenge", phoneHash + ":" + purpose + ":" + memberId);
      locks.ensure(key);
      locks.lock(key);
      mapper.supersede(phoneHash, purpose, memberId);
      var challenge = new SmsChallenge();
      challenge.setId(id);
      challenge.setPhoneHash(phoneHash);
      challenge.setPurpose(purpose);
      challenge.setMemberId(memberId);
      challenge.setCodeHash(codeHash(id, phoneHash, purpose, memberId, code));
      challenge.setState("ISSUING");
      challenge.setAttempts(0);
      challenge.setExpiresAt(now + TTL_SECONDS * 1000L);
      challenge.setCreatedAt(now);
      challenge.setDevelopment(false);
      mapper.insert(challenge);
      mapper.deleteExpiredHistory(now - 86_400_000);
    });
    SmsGateway.Delivery delivery;
    try {
      delivery = gateway.send(phone, code);
      if (delivery == null || delivery.requestId() == null || delivery.requestId().isBlank()
          || (delivery.development() && !settings.dev()))
        throw new Problem(503, "SMS_DELIVERY_FAILED", "短信发送暂不可用，请稍后重试");
      int updated = transaction.execute(status -> mapper.sent(id, delivery.requestId(), delivery.development()));
      if (updated != 1) throw new Problem(409, "SMS_SUPERSEDED", "验证码已被更新，请重新获取");
    } catch (RuntimeException failure) {
      transaction.executeWithoutResult(status -> mapper.failed(id));
      if (failure instanceof Problem problem) throw problem;
      throw new Problem(503, "SMS_DELIVERY_FAILED", "短信发送暂不可用，请稍后重试");
    }
    var response = new LinkedHashMap<String, Object>();
    response.put("challengeId", id);
    response.put("expiresIn", TTL_SECONDS);
    response.put("retryAfter", 60);
    response.put("development", delivery.development());
    if (settings.dev() && delivery.development()) response.put("devCode", code);
    return response;
  }

  public void consume(String rawPhone, String purpose, long memberId, String id, String code, String ip) {
    String phone = PhoneNumbers.normalize(rawPhone);
    requirePurpose(purpose, memberId);
    throttle.reserveVerification(ip);
    String phoneHash = protection.fingerprint("phone", phone);
    String result = transaction.execute(status -> {
      SmsChallenge row = mapper.lockById(id);
      if (row == null || !phoneHash.equals(row.getPhoneHash()) || !purpose.equals(row.getPurpose())
          || row.getMemberId() != memberId) return "INVALID";
      if (!"SENT".equals(row.getState())) return switch (row.getState()) {
        case "CONSUMED" -> "USED";
        case "LOCKED" -> "LOCKED";
        case "EXPIRED" -> "EXPIRED";
        case "SUPERSEDED" -> "SUPERSEDED";
        default -> "INVALID";
      };
      if (!settings.dev() && Boolean.TRUE.equals(row.getDevelopment())) return "DEVELOPMENT";
      if (row.getExpiresAt() <= System.currentTimeMillis()) {
        mapper.recordAttempt(id, "EXPIRED", "");
        return "EXPIRED";
      }
      boolean matched = code != null && code.matches("[0-9]{6}") && MessageDigest.isEqual(
          row.getCodeHash().getBytes(StandardCharsets.UTF_8),
          codeHash(id, phoneHash, purpose, memberId, code).getBytes(StandardCharsets.UTF_8));
      if (!matched) {
        boolean locked = row.getAttempts() + 1 >= MAX_ATTEMPTS;
        mapper.recordAttempt(id, locked ? "LOCKED" : "SENT", locked ? "" : row.getCodeHash());
        return locked ? "LOCKED" : "INVALID";
      }
      mapper.recordAttempt(id, "CONSUMED", "");
      return "OK";
    });
    if (!"OK".equals(result)) {
      String message = switch (Objects.toString(result, "INVALID")) {
        case "EXPIRED" -> "验证码已过期，请重新获取";
        case "LOCKED" -> "验证码错误次数过多，请重新获取";
        case "DEVELOPMENT" -> "开发验证码不能用于生产登录，请重新获取";
        case "USED" -> "验证码已使用，请重新获取";
        case "SUPERSEDED" -> "验证码已更新，请使用最新短信中的验证码";
        default -> "验证码无效或已使用，请核对后重试";
      };
      throw new Problem(400, "SMS_CODE_" + Objects.toString(result, "INVALID"), message);
    }
  }

  private String codeHash(String id, String phoneHash, String purpose, long memberId, String code) {
    return protection.fingerprint("sms-code", id + ":" + phoneHash + ":" + purpose + ":" + memberId + ":" + code);
  }

  private void requirePurpose(String purpose, long memberId) {
    if (!("LOGIN".equals(purpose) && memberId == 0)
        && !("BIND_PHONE".equals(purpose) && memberId > 0))
      throw new Problem(400, "SMS_PURPOSE_INVALID", "验证码用途无效");
  }
}
