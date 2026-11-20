package com.sujian.mall.service.impl.identity;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.identity.IdentityGateway;
import com.sujian.mall.mapper.MemberIdentityMapper;
import com.sujian.mall.mapper.MemberMapper;
import com.sujian.mall.service.IdentityService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IdentityServiceImpl implements IdentityService {
  @org.springframework.beans.factory.annotation.Autowired private AccountDataProtection protection;

  private final MemberIdentityMapper identities;
  private final MemberMapper members;
  private final IdentityGateway gateway;
  private final IdentityVerificationTransactions transactions;
  private final Settings settings;

  public IdentityServiceImpl(MemberIdentityMapper identities, MemberMapper members,
      IdentityGateway gateway, IdentityVerificationTransactions transactions, Settings settings) {
    this.identities = identities;
    this.members = members;
    this.gateway = gateway;
    this.transactions = transactions;
    this.settings = settings;
  }

  @Override
  public Map<String, Object> status(long memberId) {
    return IdentityViews.of(identities.selectById(memberId), settings);
  }

  @Override
  public Map<String, Object> verify(long memberId, String name, String idNumber, boolean accepted) {
    if (!accepted) throw new Problem(400, "IDENTITY_CONSENT_REQUIRED", "请先同意身份信息核验授权");
    String normalizedName = ResidentIdentity.name(name), number = ResidentIdentity.number(idNumber);
    var reservation = transactions.reserve(memberId, normalizedName, number);
    if (reservation.attemptId() == null) return reservation.current();
    try {
      var result = gateway.verify(normalizedName, number);
      if (result == null) throw new IllegalStateException("Missing verification result");
      return transactions.complete(memberId, reservation.attemptId(), result);
    } catch (RuntimeException providerFailure) {
      transactions.unknown(memberId, reservation.attemptId());
      throw new Problem(503, "IDENTITY_PROVIDER_UNAVAILABLE", "认证服务暂时不可用，尚未通过实名认证，请稍后查询或重试");
    }
  }

  @Override
  public void requireVerifiedForWithdrawal(long memberId) {
    if (!settings.identityRequiredForWithdrawal()) return;
    var row = identities.selectById(memberId);
    if (row == null || !"VERIFIED".equals(row.getState()))
      throw new Problem(409, "IDENTITY_REQUIRED", "请先完成实名认证再申请或执行提现");
    if (!settings.dev()) {
      if (Boolean.TRUE.equals(row.getDevelopment()) || "development".equals(row.getProvider()))
        throw new Problem(409, "REAL_IDENTITY_REQUIRED", "开发模拟核验不适用于真实提现，请完成正式实名认证");
      var member = Rows.required(members.selectAccountById(memberId));
      String openid = BusinessSupport.s(member, "openid");
      if (openid.isBlank() || openid.startsWith("dev:"))
        throw new Problem(409, "WECHAT_BINDING_REQUIRED", "请先在微信小程序绑定本人微信账户再提现");
    }
  }

  @Override
  public String verifiedNameForTransfer(long memberId) {
    requireVerifiedForWithdrawal(memberId);
    var row = identities.selectById(memberId);
    if (row == null || !"VERIFIED".equals(row.getState()) || Boolean.TRUE.equals(row.getDevelopment()))
      throw new Problem(409, "REAL_IDENTITY_REQUIRED", "真实出款必须使用正式核验的姓名");
    return protection.decrypt(row.getNameCiphertext());
  }

}
