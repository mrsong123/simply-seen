package com.sujian.mall.service.impl;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.mapper.SmsThrottleMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Database-backed budgets and cooldowns apply across application instances and survive send failure. */
@Service
public class SmsThrottleService {
  private final SmsThrottleMapper mapper;
  private final AccountDataProtection protection;
  private final Settings settings;
  private final TransactionTemplate transaction;

  public SmsThrottleService(SmsThrottleMapper mapper, AccountDataProtection protection,
      Settings settings, PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.protection = protection;
    this.settings = settings;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void reserve(String phoneHash, String ip) {
    int daily = settings.integer("SMS_DAILY_LIMIT", 1000);
    if (daily < 1 || daily > 1_000_000)
      throw new Problem(503, "SMS_LIMIT_CONFIGURATION", "短信日预算必须为1至1000000之间的整数");
    transaction.executeWithoutResult(status -> {
      long now = System.currentTimeMillis();
      mapper.ensureCooldown(phoneHash);
      if (mapper.lockCooldown(phoneHash) > now)
        throw new Problem(429, "SMS_COOLDOWN", "请在60秒后重新获取验证码");
      String ipHash = protection.fingerprint("sms-ip", Objects.toString(ip, "unknown"));
      var budgets = new TreeMap<String, Budget>();
      add(budgets, "phone-hour:" + phoneHash, now, 3_600_000, 5);
      add(budgets, "phone-day:" + phoneHash, now, 86_400_000, 10);
      add(budgets, "ip-minute:" + ipHash, now, 60_000, 3);
      add(budgets, "ip-hour:" + ipHash, now, 3_600_000, 20);
      add(budgets, "global-day", now, 86_400_000, daily);
      for (var entry : budgets.entrySet()) {
        mapper.ensureBucket(entry.getKey(), entry.getValue().expiresAt());
        if (mapper.lockHits(entry.getKey()) >= entry.getValue().maximum())
          throw new Problem(429, "SMS_RATE_LIMIT", "验证码发送过于频繁，请稍后再试");
        mapper.increment(entry.getKey());
      }
      mapper.updateCooldown(phoneHash, now + 60_000);
      mapper.deleteExpiredBuckets(now - 86_400_000);
    });
  }

  public void reserveVerification(String ip) {
    reserveAction("sms-check-ip", Objects.toString(ip, "unknown"), 30);
  }

  public void reserveAction(String action, String subject, int maximum) {
    transaction.executeWithoutResult(status -> {
      long now = System.currentTimeMillis();
      String scope = protection.fingerprint(action, subject);
      String bucket = protection.fingerprint("account-action-budget", scope) + ":" + now / 60_000;
      mapper.ensureBucket(bucket, (now / 60_000 + 1) * 60_000);
      if (mapper.lockHits(bucket) >= maximum)
        throw new Problem(429, "ACCOUNT_RATE_LIMIT", "操作过于频繁，请稍后重试");
      mapper.increment(bucket);
    });
  }

  private void add(Map<String, Budget> budgets, String scope, long now, long period, int maximum) {
    String bucket = protection.fingerprint("sms-budget", scope) + ":" + (now / period);
    budgets.put(bucket, new Budget((now / period + 1) * period, maximum));
  }

  private record Budget(long expiresAt, int maximum) {}
}
