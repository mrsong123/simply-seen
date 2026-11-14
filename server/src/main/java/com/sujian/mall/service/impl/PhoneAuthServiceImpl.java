package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.entity.MemberPhone;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PhoneAuthServiceImpl implements PhoneAuthService {
  private final AuthService auth;
  private final SmsChallengeService challenges;
  private final MemberMapper members;
  private final MemberPhoneMapper phones;
  private final AccountSecurityLockMapper locks;
  private final AccountDataProtection protection;
  private final BusinessSupport support;
  private final WechatClient wechat;
  private final SmsThrottleService throttle;
  private final TransactionTemplate transaction;

  public PhoneAuthServiceImpl(AuthService auth, SmsChallengeService challenges, MemberMapper members,
      MemberPhoneMapper phones, AccountSecurityLockMapper locks, AccountDataProtection protection,
      BusinessSupport support, WechatClient wechat, SmsThrottleService throttle,
      PlatformTransactionManager manager) {
    this.auth = auth;
    this.challenges = challenges;
    this.members = members;
    this.phones = phones;
    this.locks = locks;
    this.protection = protection;
    this.support = support;
    this.wechat = wechat;
    this.throttle = throttle;
    transaction = new TransactionTemplate(manager);
  }

  public Map<String, Object> sendLoginCode(String phone, String ip) {
    return challenges.send(PhoneNumbers.normalize(phone), "LOGIN", 0, ip);
  }

  public Map<String, Object> login(String rawPhone, String code, String challengeId, String inviteCode,
      boolean accepted, String ip) {
    Problem.require(accepted, "AGREEMENT_REQUIRED", "请阅读并同意服务规则及隐私政策");
    String phone = PhoneNumbers.normalize(rawPhone);
    challenges.consume(phone, "LOGIN", 0, challengeId, code, ip);
    return transaction.execute(status -> loginVerifiedPhone(phone, inviteCode));
  }

  private Map<String, Object> loginVerifiedPhone(String phone, String inviteCode) {
    String hash = protection.fingerprint("phone", phone);
    lock("phone-account", hash);
    MemberPhone bound = phones.findByPhoneHash(hash);
    long id;
    if (bound != null) {
      id = bound.getMemberId();
      activeMember(id);
    } else {
      Long inviter = null;
      if (inviteCode != null && !inviteCode.isBlank())
        inviter = n(Rows.required(members.selectEligibleInviterByCode(inviteCode.trim())), "id");
      id = support.id();
      members.insertRegisteredMember(id, null, "素见用户" + phone.substring(7),
          Long.toString(id, 36).toUpperCase(Locale.ROOT), inviter, support.now());
      savePhone(id, phone, hash);
      support.audit(id, "PHONE_REGISTER", String.valueOf(id), Map.of("agreementVersion", "TERMS-PHONE-1.0"));
    }
    return auth.session(id, "MEMBER");
  }

  public Map<String, Object> sendBindingCode(long memberId, String rawPhone, String ip) {
    String phone = PhoneNumbers.normalize(rawPhone);
    activeMember(memberId);
    checkCurrentBinding(memberId, protection.fingerprint("phone", phone));
    return challenges.send(phone, "BIND_PHONE", memberId, ip);
  }

  public Map<String, Object> bindPhone(long memberId, String rawPhone, String code, String challengeId, String ip) {
    String phone = PhoneNumbers.normalize(rawPhone);
    String hash = protection.fingerprint("phone", phone);
    // OTP owns only its challenge row; account locks are acquired after its independent commit.
    challenges.consume(phone, "BIND_PHONE", memberId, challengeId, code, ip);
    return transaction.execute(status -> bindVerifiedPhone(memberId, phone, hash));
  }

  private Map<String, Object> bindVerifiedPhone(long memberId, String phone, String hash) {
    lock("phone-account", hash);
    activeMember(memberId);
    checkBinding(memberId, hash);
    if (phones.selectById(memberId) == null) savePhone(memberId, phone, hash);
    support.audit(memberId, "PHONE_BOUND", String.valueOf(memberId), Map.of("phoneMasked", PhoneNumbers.masked(phone)));
    return auth.profile(memberId);
  }

  public Map<String, Object> bindWechat(long memberId, String code, String ip) {
    if (code == null || code.isBlank()) throw new Problem(400, "WX_CODE_REQUIRED", "缺少微信登录凭证");
    throttle.reserveAction("wechat-binding-member", String.valueOf(memberId), 5);
    throttle.reserveAction("wechat-binding-ip", Objects.toString(ip, "unknown"), 10);
    String openid = s(wechat.login(code), "openid");
    if (openid.isBlank() || openid.startsWith("dev:"))
      throw new Problem(400, "WX_LOGIN_FAILED", "微信身份验证失败，请重新授权");
    return transaction.execute(status -> bindVerifiedWechat(memberId, openid));
  }

  private Map<String, Object> bindVerifiedWechat(long memberId, String openid) {
    lock("wechat-account", openid);
    var member = activeMember(memberId);
    String current = s(member, "openid");
    if (!current.isBlank() && !current.startsWith("dev:") && !current.equals(openid))
      throw new Problem(409, "WECHAT_ALREADY_BOUND", "账户已绑定微信，不能直接更换");
    var existing = members.selectByOpenid(openid);
    if (existing != null && n(existing, "id") != memberId)
      throw new Problem(409, "WECHAT_IN_USE", "该微信已关联其他会员，请使用原账户登录后绑定手机号");
    if (!current.equals(openid) && members.bindWechatOpenid(memberId, openid) != 1)
      throw new Problem(409, "WECHAT_ALREADY_BOUND", "微信绑定状态已变化，请刷新后重试");
    support.audit(memberId, "WECHAT_BOUND", String.valueOf(memberId), Map.of());
    return auth.profile(memberId);
  }

  private Map<String, Object> activeMember(long memberId) {
    var member = Rows.required(members.lockAccountById(memberId));
    Problem.require(!b(member, "blocked"), "ACCOUNT_DISABLED", "账号已停用");
    return member;
  }

  private void checkBinding(long memberId, String hash) {
    checkCurrentBinding(memberId, hash);
    MemberPhone occupied = phones.findByPhoneHash(hash);
    if (occupied != null && !occupied.getMemberId().equals(memberId))
      throw new Problem(409, "PHONE_IN_USE", "该手机号已关联其他会员，请使用原账户登录，系统不会自动合并账户");
  }

  private void checkCurrentBinding(long memberId, String hash) {
    MemberPhone current = phones.selectById(memberId);
    if (current != null && !current.getPhoneHash().equals(hash))
      throw new Problem(409, "PHONE_ALREADY_BOUND", "账户已绑定手机号，更换需通过客服核验");
  }

  private void lock(String context, String value) {
    String key = protection.fingerprint(context, value);
    locks.ensure(key);
    locks.lock(key);
  }

  private void savePhone(long id, String phone, String hash) {
    var row = new MemberPhone();
    row.setMemberId(id);
    row.setPhoneHash(hash);
    row.setPhoneCiphertext(protection.encrypt(phone));
    row.setPhoneMasked(PhoneNumbers.masked(phone));
    row.setCreatedAt(support.now());
    phones.insert(row);
  }
}
