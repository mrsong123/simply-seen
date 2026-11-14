package com.sujian.mall.service.impl.identity;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.entity.*;
import com.sujian.mall.integration.identity.IdentityGateway;
import com.sujian.mall.mapper.*;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Keeps paid-provider reservations and attempts durable independently of HTTP failures. */
@Service
public class IdentityVerificationTransactions {
  public record Reservation(String attemptId, Map<String, Object> current) {}

  private final MemberIdentityMapper identities;
  private final IdentityAttemptMapper attempts;
  private final MemberMapper members;
  private final AccountDataProtection protection;
  private final Settings settings;
  private final BusinessSupport support;
  private final TransactionTemplate transaction;

  public IdentityVerificationTransactions(MemberIdentityMapper identities,
      IdentityAttemptMapper attempts, MemberMapper members, AccountDataProtection protection,
      Settings settings, BusinessSupport support, PlatformTransactionManager manager) {
    this.identities = identities;
    this.attempts = attempts;
    this.members = members;
    this.protection = protection;
    this.settings = settings;
    this.support = support;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Reservation reserve(long memberId, String name, String number) {
    return transaction.execute(ignored -> reserveInside(memberId, name, number));
  }

  private Reservation reserveInside(long memberId, String name, String number) {
    var member = Rows.required(members.lockAccountById(memberId));
    if (b(member, "blocked")) throw new Problem(403, "ACCOUNT_DISABLED", "账号已停用");
    long now = support.now(), day = Math.floorDiv(now, 86_400_000L);
    String credential = protection.fingerprint("identity-input", name + "\u0000" + number);
    var row = identities.lockByMemberId(memberId);
    if (row != null && "VERIFIED".equals(row.getState())
        && (settings.dev() || !Boolean.TRUE.equals(row.getDevelopment()))) {
      if (!credential.equals(row.getCredentialHash()))
        throw new Problem(409, "IDENTITY_ALREADY_VERIFIED", "已完成认证，身份信息变更请联系客服处理");
      return new Reservation(null, IdentityViews.of(row, settings));
    }
    long pendingLease = IdentityViews.pendingSeconds(settings) * 1000L;
    if (row != null && "PENDING".equals(row.getState()) && now - row.getLastAttemptAt() < pendingLease) {
      if (!credential.equals(row.getCredentialHash()))
        throw new Problem(409, "IDENTITY_IN_PROGRESS", "已有身份核验正在处理，请稍后查询结果");
      return new Reservation(null, IdentityViews.of(row, settings));
    }
    int count = row != null && row.getAttemptDay() == day ? row.getAttemptCount() : 0;
    int max = Math.max(1, Math.min(10, settings.integer("IDENTITY_MAX_ATTEMPTS_PER_DAY", 5)));
    if (count >= max) throw new Problem(429, "IDENTITY_DAILY_LIMIT", "今日身份核验次数已达上限，请明日再试");
    long cooldown = IdentityViews.cooldownSeconds(settings) * 1000L;
    if (row != null && now - row.getLastAttemptAt() < cooldown)
      throw new Problem(429, "IDENTITY_COOLDOWN", "身份核验操作频繁，请稍后再试");
    String attemptId = support.no("IDV");
    boolean insert = row == null;
    if (row == null) {
      row = new MemberIdentity();
      row.setMemberId(memberId);
      row.setCreatedAt(now);
    } else if ("PENDING".equals(row.getState())) {
      attempts.finish(row.getActiveAttemptId(), "UNKNOWN", "", false, "核验租约到期，等待重新核验", now);
    }
    row.setState("PENDING");
    row.setCredentialHash(credential);
    row.setIdNumberHash(protection.fingerprint("resident-id", number));
    row.setVerifiedIdHash(null);
    row.setNameCiphertext(protection.encrypt(name));
    row.setIdNumberCiphertext(protection.encrypt(number));
    row.setNameMasked(ResidentIdentity.maskName(name));
    row.setIdNumberMasked(ResidentIdentity.maskNumber(number));
    row.setProvider(settings.get("IDENTITY_PROVIDER", settings.dev() ? "development" : "aliyun"));
    row.setProviderRequestId("");
    row.setDevelopment("development".equals(row.getProvider()));
    row.setVerifiedAt(null);
    row.setRejectionReason("核验结果尚未确认，请稍后查询");
    row.setActiveAttemptId(attemptId);
    row.setLastAttemptAt(now);
    row.setAttemptDay(day);
    row.setAttemptCount(count + 1);
    row.setConsentVersion("IDENTITY-1.0");
    row.setConsentAt(now);
    row.setUpdatedAt(now);
    if (insert) identities.insert(row);
    else identities.updateById(row);
    var attempt = new IdentityAttempt();
    attempt.setId(attemptId);
    attempt.setMemberId(memberId);
    attempt.setState("PENDING");
    attempt.setProvider(row.getProvider());
    attempt.setProviderRequestId("");
    attempt.setDevelopment(row.getDevelopment());
    attempt.setReason("");
    attempt.setCreatedAt(now);
    attempts.insert(attempt);
    return new Reservation(attemptId, IdentityViews.of(row, settings));
  }

  public Map<String, Object> complete(long memberId, String attemptId, IdentityGateway.Verification result) {
    try {
      return transaction.execute(ignored -> completeInside(memberId, attemptId, result));
    } catch (DataIntegrityViolationException duplicate) {
      return rejectDuplicate(memberId, attemptId);
    }
  }

  private Map<String, Object> completeInside(long memberId, String attemptId, IdentityGateway.Verification result) {
    Rows.required(members.lockAccountById(memberId));
    var row = identities.lockByMemberId(memberId);
    if (row == null || !attemptId.equals(row.getActiveAttemptId())) {
      attempts.finish(attemptId, "STALE", "", false, "后续核验已替代本次请求", support.now());
      return IdentityViews.of(row, settings);
    }
    if (result.development() && !settings.dev())
      throw new Problem(503, "IDENTITY_PROVIDER_INVALID", "认证渠道配置异常，尚未通过实名认证");
    row.setState(result.matched() ? "VERIFIED" : "REJECTED");
    row.setVerifiedIdHash(result.matched() ? row.getIdNumberHash() : null);
    row.setProviderRequestId(requestId(result.requestId()));
    row.setDevelopment(result.development());
    row.setVerifiedAt(result.matched() ? support.now() : null);
    row.setRejectionReason(result.matched() ? "" : "姓名与身份证信息未通过匹配，请核对后重试");
    row.setUpdatedAt(support.now());
    identities.completeAttempt(row, attemptId);
    attempts.finish(attemptId, row.getState(), row.getProviderRequestId(), result.development(),
        row.getRejectionReason(), support.now());
    support.audit(memberId, "IDENTITY_VERIFICATION", attemptId,
        Map.of("status", row.getState(), "provider", row.getProvider(), "development", result.development()));
    return IdentityViews.of(row, settings);
  }

  private Map<String, Object> rejectDuplicate(long memberId, String attemptId) {
    return transaction.execute(ignored -> {
      Rows.required(members.lockAccountById(memberId));
      var row = identities.lockByMemberId(memberId);
      if (row != null && attemptId.equals(row.getActiveAttemptId())) {
        row.setState("REJECTED");
        row.setVerifiedIdHash(null);
        row.setVerifiedAt(null);
        row.setRejectionReason("该身份证已被其他账户认证，请联系客服处理");
        row.setUpdatedAt(support.now());
        identities.completeAttempt(row, attemptId);
        attempts.finish(attemptId, "REJECTED", "", false, row.getRejectionReason(), support.now());
      }
      return IdentityViews.of(row, settings);
    });
  }

  public void unknown(long memberId, String attemptId) {
    transaction.executeWithoutResult(ignored -> {
      Rows.required(members.lockAccountById(memberId));
      var row = identities.lockByMemberId(memberId);
      if (row == null || !attemptId.equals(row.getActiveAttemptId())) return;
      row.setState("PENDING");
      row.setVerifiedIdHash(null);
      row.setVerifiedAt(null);
      row.setRejectionReason("核验结果暂未确认，请稍后查询或重新填写后重试");
      row.setUpdatedAt(support.now());
      identities.completeAttempt(row, attemptId);
      attempts.finish(attemptId, "UNKNOWN", "", Boolean.TRUE.equals(row.getDevelopment()),
          row.getRejectionReason(), support.now());
    });
  }

  private static String requestId(String value) {
    return value != null && value.matches("[A-Za-z0-9._:-]{1,160}") ? value : "";
  }
}
