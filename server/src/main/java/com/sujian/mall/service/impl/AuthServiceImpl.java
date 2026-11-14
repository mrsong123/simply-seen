package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
  @org.springframework.beans.factory.annotation.Autowired
  private AdminAccountMapper adminAccountMapper;

  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SessionTokenMapper sessionTokenMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final WechatClient w;
  private final RateLimitService limits;

  public AuthServiceImpl(BusinessSupport d, Settings c, WechatClient w, RateLimitService limits) {
    this.d = d;
    this.c = c;
    this.w = w;
    this.limits = limits;
  }

  private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwords =
      new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);

  public String encodePassword(String value) {
    return passwords.encode(value);
  }

  public Actor actor(HttpServletRequest req) {
    String bearer = req.getHeader("Authorization");
    if (bearer == null || !bearer.startsWith("Bearer "))
      throw new Problem(401, "AUTH_REQUIRED", "请先登录");
    var tokens = sessionTokenMapper.listForActor(hash(bearer.substring(7)), d.now());
    if (tokens.isEmpty()) throw new Problem(401, "AUTH_REQUIRED", "登录已失效，请重新登录");
    var t = tokens.getFirst();
    long id = n(t, "actor_id");
    String kind = s(t, "kind");
    if (kind.equals("ADMIN")) {
      var a = com.sujian.mall.common.Rows.required(adminAccountMapper.findForActor(id));
      return new Actor(id, kind, s(a, "role"));
    }
    com.sujian.mall.common.Rows.required(memberMapper.selectActiveMemberId(id));
    return new Actor(id, kind, "MEMBER");
  }

  public Actor member(HttpServletRequest req) {
    var a = actor(req);
    if (!a.kind().equals("MEMBER")) throw new Problem(403, "FORBIDDEN", "需要会员身份");
    return a;
  }

  public Actor admin(HttpServletRequest req, String... roles) {
    var a = actor(req);
    if (!a.kind().equals("ADMIN")
        || (!a.role().equals("ADMIN") && !List.of(roles).contains(a.role())))
      throw new Problem(403, "FORBIDDEN", "当前账号没有操作权限");
    return a;
  }

  public Map<String, Object> session(long id, String kind) {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    sessionTokenMapper.insertForSession(
        hash(token), id, kind, d.now() + c.integer("SESSION_HOURS", 168) * 3600000L);
    return Map.of(
        "token",
        token,
        "kind",
        kind,
        "profile",
        kind.equals("MEMBER")
            ? profile(id)
            : com.sujian.mall.common.Rows.required(adminAccountMapper.findForAdminMe(id)));
  }

  @Transactional
  public Map<String, Object> login(Map<String, Object> body, String ip) {
    limits.limit("login:" + ip, 30);
    String code = s(body, "code");
    Problem.require(!code.isBlank(), "INVALID_INPUT", "缺少微信登录凭证");
    String openid = s(w.login(code), "openid");
    return loginOpenid(openid, s(body, "inviteCode"));
  }

  @Transactional
  public Map<String, Object> devLogin(Map<String, Object> body) {
    if (!c.dev()) throw new Problem(404, "NOT_FOUND", "入口不存在");
    String name = s(body, "name").trim();
    Problem.require(name.matches("[\\p{L}0-9_ -]{2,30}"), "INVALID_INPUT", "姓名需为2至30个字符");
    return loginOpenid("dev:" + name, s(body, "inviteCode"));
  }

  public void limit(String bucket, int max) {
    limits.limit(bucket, max);
  }

  private Map<String, Object> loginOpenid(String openid, String invite) {
    Problem.require(!openid.isBlank(), "WX_LOGIN_FAILED", "微信登录失败");
    if (memberMapper.countByOpenid(openid) > 0) {
      var m = com.sujian.mall.common.Rows.required(memberMapper.selectByOpenid(openid));
      Problem.require(!b(m, "blocked"), "ACCOUNT_DISABLED", "账号已停用");
      return session(n(m, "id"), "MEMBER");
    }
    Long inviter = null;
    if (!invite.isBlank())
      inviter =
          n(
              com.sujian.mall.common.Rows.required(
                  memberMapper.selectEligibleInviterByCode(invite)),
              "id");
    long id = d.id();
    memberMapper.insertRegisteredMember(
        id, openid, "素见会员", Long.toString(id, 36).toUpperCase(), inviter, d.now());
    return session(id, "MEMBER");
  }

  @Transactional
  public Map<String, Object> adminLogin(Map<String, Object> body, String ip) {
    limits.limit("admin:" + ip, 10);
    String u = s(body, "username"), p = s(body, "password");
    var rows = adminAccountMapper.listForAdminLogin(u);
    boolean ok = !rows.isEmpty() && passwords.matches(p, s(rows.getFirst(), "password_hash"));
    if (!ok) throw new Problem(401, "LOGIN_FAILED", "账号或密码错误");
    return session(n(rows.getFirst(), "id"), "ADMIN");
  }

  public Map<String, Object> profile(long id) {
    return com.sujian.mall.common.Rows.required(memberMapper.selectPublicProfile(id));
  }

  @Transactional
  public void updateProfile(long id, Map<String, Object> body) {
    String name = s(body, "name").trim();
    Problem.require(name.length() >= 2 && name.length() <= 40, "INVALID_INPUT", "昵称需为2至40个字符");
    memberMapper.updateDisplayName(name, id);
  }

  @Transactional
  public void promote(long id) {
    memberMapper.acceptPromoterAgreement(id);
    d.audit(id, "PROMOTER_ACCEPT", String.valueOf(id), Map.of("version", "DIST-1.0"));
  }

  @Transactional
  public void logout(HttpServletRequest r) {
    String token = r.getHeader("Authorization");
    if (token != null && token.startsWith("Bearer "))
      sessionTokenMapper.deleteForLogout(hash(token.substring(7)));
  }

  @Transactional
  public void createAdmin(long actor, Map<String, Object> body) {
    String role = s(body, "role"), name = s(body, "username"), pass = s(body, "password");
    Problem.require(
        List.of("ADMIN", "OPERATOR", "FINANCE").contains(role)
            && name.matches("[a-zA-Z0-9_-]{3,40}")
            && pass.length() >= 12,
        "INVALID_INPUT",
        "账号至少3位，密码至少12位，角色需有效");
    adminAccountMapper.insertForCreateAdmin(d.id(), name, passwords.encode(pass), role);
    d.audit(actor, "ADMIN_CREATE", name, Map.of("role", role));
  }

  @Transactional
  public void password(long id, Map<String, Object> body) {
    var a = com.sujian.mall.common.Rows.required(adminAccountMapper.findForAccountActive(id));
    String next = s(body, "password");
    Problem.require(
        passwords.matches(s(body, "currentPassword"), s(a, "password_hash")) && next.length() >= 12,
        "INVALID_INPUT",
        "原密码错误或新密码不足12位");
    adminAccountMapper.updateForResetPassword(passwords.encode(next), id);
    sessionTokenMapper.deleteForAccountActive(id);
  }
}
