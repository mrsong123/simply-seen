package com.sujian.mall.service.impl.bootstrap;

import com.sujian.mall.common.*;
import com.sujian.mall.config.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AdminBootstrapService {
  @org.springframework.beans.factory.annotation.Autowired
  private AdminAccountMapper adminAccountMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final AuthService auth;
  private final LocalCredentialsStore credentials;

  public AdminBootstrapService(
      BusinessSupport d, Settings c, AuthService auth, LocalCredentialsStore credentials) {
    this.d = d;
    this.c = c;
    this.auth = auth;
    this.credentials = credentials;
  }

  public void initialize() {
    if (adminAccountMapper.countForRun() > 0) return;
    String username = c.get("ADMIN_BOOTSTRAP_USER", "admin");
    String password = c.get("ADMIN_BOOTSTRAP_PASSWORD", "");
    if (password.isBlank() && c.dev()) {
      var generated = credentials.obtain(c);
      username = generated.username();
      password = generated.password();
    }
    Problem.require(
        password.length() >= 12, "WEAK_PASSWORD", "管理员初始密码至少12位；生产必须设置ADMIN_BOOTSTRAP_PASSWORD");
    adminAccountMapper.insertForCreateAdmin(
        d.id(), username, auth.encodePassword(password), "ADMIN");
  }
}
