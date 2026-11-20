package com.sujian.mall.service.impl.admin;

import static com.sujian.mall.common.BusinessSupport.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sujian.mall.common.BusinessSupport;
import com.sujian.mall.common.Problem;
import com.sujian.mall.entity.Address;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.AuthService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAdminOperations {
  @org.springframework.beans.factory.annotation.Autowired private AddressMapper addressMapper;
  @org.springframework.beans.factory.annotation.Autowired private CommissionMapper commissionMapper;
  @org.springframework.beans.factory.annotation.Autowired private LedgerMapper ledgerMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SessionTokenMapper sessionTokenMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private SettlementAwardMapper settlementAwardMapper;

  @org.springframework.beans.factory.annotation.Autowired private WithdrawalMapper withdrawalMapper;

  private final BusinessSupport d;
  private final AuthService auth;

  public MemberAdminOperations(BusinessSupport d, AuthService auth) {
    this.d = d;
    this.auth = auth;
  }

  public Object ledger(long uid) {
    return ledgerMapper.listForLedger(uid);
  }

  public Object team(long uid) {
    var first = memberMapper.selectDirectInvitees(uid);
    var second = memberMapper.selectSecondLevelInvitees(uid);
    return Map.of(
        "level1", first, "level2", second, "commissions", commissionMapper.listForTeam(uid));
  }

  public Object withdrawals(long uid) {
    return withdrawalMapper.listForWithdrawals(uid);
  }

  public Object awards(long uid) {
    return settlementAwardMapper.listForAwards(uid);
  }

  public void deleteAddress(long id, long uid) {
    addressMapper.delete(
        Wrappers.<Address>lambdaQuery().eq(Address::getId, id).eq(Address::getMemberId, uid));
  }

  public Object members() {
    return memberMapper.selectAdminMemberProfiles();
  }

  @Transactional
  public void block(long actor, long id, Map<String, Object> b) {
    com.sujian.mall.common.Rows.required(memberMapper.lockAccountById(id));
    memberMapper.setBlocked(b(b, "blocked"), id);
    if (b(b, "blocked")) sessionTokenMapper.deleteForBlock(id);
    d.audit(actor, "MEMBER_BLOCK", "" + id, b);
  }

  public void acceptPromoter(long uid, Map<String, Object> b) {
    Problem.require(b(b, "accepted"), "AGREEMENT_REQUIRED", "请阅读并同意推广规则");
    auth.promote(uid);
  }
}
