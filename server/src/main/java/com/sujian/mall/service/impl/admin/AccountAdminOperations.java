package com.sujian.mall.service.impl.admin;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.BusinessSupport;
import com.sujian.mall.common.Problem;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.AuthService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdminOperations {
  @org.springframework.beans.factory.annotation.Autowired
  private AdminAccountMapper adminAccountMapper;

  @org.springframework.beans.factory.annotation.Autowired private AuditMapper auditMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SessionTokenMapper sessionTokenMapper;

  @org.springframework.beans.factory.annotation.Autowired private SystemGuardMapper guards;

  private final BusinessSupport d;
  private final AuthService auth;

  public AccountAdminOperations(BusinessSupport d, AuthService auth) {
    this.d = d;
    this.auth = auth;
  }

  public Object adminMe(long actor) {
    return com.sujian.mall.common.Rows.required(adminAccountMapper.findForAdminMe(actor));
  }

  public Object accounts() {
    return adminAccountMapper.listForAccounts();
  }

  public void createAccount(long actor, Map<String, Object> b) {
    auth.createAdmin(actor, b);
  }

  @Transactional
  public void accountActive(long actor, long id, Map<String, Object> b) {
    com.sujian.mall.common.Rows.required(guards.lockAdminAccounts());
    com.sujian.mall.common.Rows.required(adminAccountMapper.findForActor(actor));
    Problem.require(id != actor, "SELF_DISABLE", "不能停用当前登录账号");
    var target = com.sujian.mall.common.Rows.required(adminAccountMapper.findForAccountActive(id));
    boolean enabled = b(b, "active");
    if (!enabled && s(target, "role").equals("ADMIN"))
      Problem.require(adminAccountMapper.countForAccountActive() > 1, "LAST_ADMIN", "不能停用最后一个管理员");
    adminAccountMapper.updateForAccountActive(enabled, id);
    if (!enabled) sessionTokenMapper.deleteForAccountActive(id);
    d.audit(actor, "ACCOUNT_ACTIVE", "" + id, b);
  }

  @Transactional
  public void resetPassword(long actor, long id, Map<String, Object> b) {
    Problem.require(id != actor, "SELF_RESET", "请使用个人修改密码功能");
    String password = s(b, "password");
    Problem.require(password.length() >= 12, "WEAK_PASSWORD", "新密码至少12位");
    com.sujian.mall.common.Rows.required(adminAccountMapper.findForResetPassword(id));
    adminAccountMapper.updateForResetPassword(auth.encodePassword(password), id);
    sessionTokenMapper.deleteForAccountActive(id);
    d.audit(actor, "ACCOUNT_PASSWORD_RESET", "" + id, Map.of());
  }

  public Object audit() {
    return auditMapper.listForAudit();
  }
}
