package com.sujian.mall;

import static com.sujian.mall.Db.*;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Auth {
  private final Db d;
  private final Settings c;
  private final Wechat w;
  private final org.springframework.transaction.support.TransactionTemplate limitTx;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

  public Auth(
      Db d, Settings c, Wechat w, org.springframework.transaction.PlatformTransactionManager tm) {
    this.d = d;
    this.c = c;
    this.w = w;
    limitTx = new org.springframework.transaction.support.TransactionTemplate(tm);
    limitTx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public String encodePassword(String value) {
    return passwords.encode(value);
  }

  public record Actor(long id, String kind, String role) {}

  public Actor actor(HttpServletRequest req) {
    String bearer = req.getHeader("Authorization");
    if (bearer == null || !bearer.startsWith("Bearer "))
      throw new Problem(401, "AUTH_REQUIRED", "请先登录");
    var tokens =
        d.all(
            "SELECT * FROM session_token WHERE token_hash=? AND expires_at>?",
            hash(bearer.substring(7)),
            d.now());
    if (tokens.isEmpty()) throw new Problem(401, "AUTH_REQUIRED", "登录已失效，请重新登录");
    var t = tokens.getFirst();
    long id = n(t, "actor_id");
    String kind = s(t, "kind");
    if (kind.equals("ADMIN")) {
      var a = d.one("SELECT * FROM admin_account WHERE id=? AND active=TRUE", id);
      return new Actor(id, kind, s(a, "role"));
    }
    d.one("SELECT id FROM member WHERE id=? AND blocked=FALSE", id);
    return new Actor(id, kind, "MEMBER");
  }

  public Actor member(HttpServletRequest req) {
    var a = actor(req);
    if (!a.kind.equals("MEMBER")) throw new Problem(403, "FORBIDDEN", "需要会员身份");
    return a;
  }

  public Actor admin(HttpServletRequest req, String... roles) {
    var a = actor(req);
    if (!a.kind.equals("ADMIN") || (!a.role.equals("ADMIN") && !List.of(roles).contains(a.role)))
      throw new Problem(403, "FORBIDDEN", "当前账号没有操作权限");
    return a;
  }

  public Map<String, Object> session(long id, String kind) {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    d.exec(
        "INSERT INTO session_token VALUES(?,?,?,?)",
        hash(token),
        id,
        kind,
        d.now() + c.integer("SESSION_HOURS", 168) * 3600000L);
    return Map.of(
        "token",
        token,
        "kind",
        kind,
        "profile",
        kind.equals("MEMBER")
            ? profile(id)
            : d.one("SELECT id,username,role FROM admin_account WHERE id=?", id));
  }

  @Transactional
  public Map<String, Object> login(Map<String, Object> body, String ip) {
    limit("login:" + ip, 30);
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

  private Map<String, Object> loginOpenid(String openid, String invite) {
    Problem.require(!openid.isBlank(), "WX_LOGIN_FAILED", "微信登录失败");
    if (d.count("SELECT COUNT(*) FROM member WHERE openid=?", openid) > 0) {
      var m = d.one("SELECT * FROM member WHERE openid=?", openid);
      Problem.require(!b(m, "blocked"), "ACCOUNT_DISABLED", "账号已停用");
      return session(n(m, "id"), "MEMBER");
    }
    Long inviter = null;
    if (!invite.isBlank())
      inviter =
          n(
              d.one(
                  "SELECT * FROM member WHERE invite_code=? AND promoter=TRUE AND blocked=FALSE",
                  invite),
              "id");
    long id = d.id();
    d.exec(
        "INSERT INTO member(id,openid,name,invite_code,inviter_id,created_at) VALUES(?,?,?,?,?,?)",
        id,
        openid,
        "素见会员",
        Long.toString(id, 36).toUpperCase(),
        inviter,
        d.now());
    return session(id, "MEMBER");
  }

  @Transactional
  public Map<String, Object> adminLogin(Map<String, Object> body, String ip) {
    limit("admin:" + ip, 10);
    String u = s(body, "username"), p = s(body, "password");
    var rows = d.all("SELECT * FROM admin_account WHERE username=? AND active=TRUE", u);
    boolean ok = !rows.isEmpty() && passwords.matches(p, s(rows.getFirst(), "password_hash"));
    if (!ok) throw new Problem(401, "LOGIN_FAILED", "账号或密码错误");
    return session(n(rows.getFirst(), "id"), "ADMIN");
  }

  public Map<String, Object> profile(long id) {
    return d.one(
        "SELECT"
            + " id,name,invite_code,inviter_id,points,points_held,available,reserved,debt,promoter,created_at"
            + " FROM member WHERE id=?",
        id);
  }

  @Transactional
  public void updateProfile(long id, Map<String, Object> body) {
    String name = s(body, "name").trim();
    Problem.require(name.length() >= 2 && name.length() <= 40, "INVALID_INPUT", "昵称需为2至40个字符");
    d.exec("UPDATE member SET name=? WHERE id=?", name, id);
  }

  @Transactional
  public void promote(long id) {
    d.exec("UPDATE member SET promoter=TRUE WHERE id=? AND blocked=FALSE", id);
    d.audit(id, "PROMOTER_ACCEPT", String.valueOf(id), Map.of("version", "DIST-1.0"));
  }

  @Transactional
  public void logout(HttpServletRequest r) {
    String token = r.getHeader("Authorization");
    if (token != null && token.startsWith("Bearer "))
      d.exec("DELETE FROM session_token WHERE token_hash=?", hash(token.substring(7)));
  }

  public void limit(String bucket, int max) {
    limitTx.executeWithoutResult(t -> limitInside(bucket, max));
  }

  private void limitInside(String bucket, int max) {
    long now = d.now();
    var row = d.all("SELECT * FROM rate_limit WHERE bucket=? FOR UPDATE", bucket);
    if (row.isEmpty()) {
      d.exec("INSERT INTO rate_limit VALUES(?,1,?)", bucket, now);
      return;
    }
    if (now - n(row.getFirst(), "window_at") > 60000) {
      d.exec("UPDATE rate_limit SET hits=1,window_at=? WHERE bucket=?", now, bucket);
      return;
    }
    if (n(row.getFirst(), "hits") >= max) throw new Problem(429, "RATE_LIMIT", "操作频繁，请稍后再试");
    d.exec("UPDATE rate_limit SET hits=hits+1 WHERE bucket=?", bucket);
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
    d.exec(
        "INSERT INTO admin_account VALUES(?,?,?,?,TRUE)",
        d.id(),
        name,
        passwords.encode(pass),
        role);
    d.audit(actor, "ADMIN_CREATE", name, Map.of("role", role));
  }

  @Transactional
  public void password(long id, Map<String, Object> body) {
    var a = d.one("SELECT * FROM admin_account WHERE id=? FOR UPDATE", id);
    String next = s(body, "password");
    Problem.require(
        passwords.matches(s(body, "currentPassword"), s(a, "password_hash")) && next.length() >= 12,
        "INVALID_INPUT",
        "原密码错误或新密码不足12位");
    d.exec("UPDATE admin_account SET password_hash=? WHERE id=?", passwords.encode(next), id);
    d.exec("DELETE FROM session_token WHERE actor_id=? AND kind='ADMIN'", id);
  }
}
