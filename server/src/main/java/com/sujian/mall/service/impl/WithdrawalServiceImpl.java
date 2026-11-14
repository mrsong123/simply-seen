package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalServiceImpl implements WithdrawalService {
  @org.springframework.beans.factory.annotation.Autowired private IssueMapper issueMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;
  @org.springframework.beans.factory.annotation.Autowired private WithdrawalMapper withdrawalMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final CommerceService shop;

  public WithdrawalServiceImpl(BusinessSupport d, Settings c, CommerceService shop) {
    this.d = d;
    this.c = c;
    this.shop = shop;
  }

  @Transactional
  public Object withdraw(long uid, String key, Map<String, Object> b) {
    return shop.idem(
        uid,
        "withdraw",
        key,
        b,
        () -> {
          Problem.require(
              c.enabled("TRANSFER_ENABLED") || c.dev(), "TRANSFER_DISABLED", "提现服务尚未开放");
          long amount = n(b, "amountFen");
          var m = com.sujian.mall.common.Rows.required(memberMapper.lockAccountById(uid));
          Problem.require(
              amount >= c.integer("WITHDRAW_MIN_FEN", 1000)
                  && amount <= Math.min(200000, c.integer("WITHDRAW_MAX_FEN", 200000))
                  && n(m, "available") >= amount
                  && n(m, "debt") == 0,
              "WITHDRAW_LIMIT",
              "余额不足、存在待追偿款项或金额超出提现范围");
          Problem.require(
              issueMapper.countForWithdraw(uid) == 0, "RISK_REVIEW", "存在未完成积分回收，请先联系客服处理");
          String id = d.no("WD");
          shop.asset(uid, "available", -amount, id + ":hold", null, "申请提现预留");
          shop.asset(uid, "reserved", amount, id + ":reserve", null, "待审核提现冻结");
          withdrawalMapper.insertForWithdraw(id, uid, amount, amount, d.now());
          return Map.of("id", id, "state", "REVIEWING");
        });
  }

  @Transactional
  public void reviewWithdrawal(long actor, String id, Map<String, Object> body) {
    var r = com.sujian.mall.common.Rows.required(withdrawalMapper.findForReviewWithdrawal(id));
    Problem.require(s(r, "state").equals("REVIEWING"), "WITHDRAW_STATE", "提现单已处理");
    boolean approve = b(body, "approve");
    String reason = s(body, "reason");
    Problem.require(
        reason.length() >= 2 && reason.length() <= 240, "REASON_REQUIRED", "请填写审核依据或驳回原因");
    if (!approve) {
      releaseWithdrawal(r, "REJECTED");
      withdrawalMapper.updateForReviewWithdrawal(actor, reason, id);
    } else {
      long tax = n(body, "taxFen");
      Problem.require(tax >= 0 && tax < n(r, "gross"), "INVALID_TAX", "税额必须小于税前金额");
      withdrawalMapper.updateForReviewWithdrawal2(actor, tax, tax, reason, id);
    }
    d.audit(actor, "WITHDRAW_REVIEW", id, body);
  }

  @Transactional
  public void executeWithdrawal(long actor, String id) {
    var r = com.sujian.mall.common.Rows.required(withdrawalMapper.findForReviewWithdrawal(id));
    Problem.require(
        s(r, "state").equals("APPROVED") && n(r, "reviewer") != actor,
        "DUAL_REVIEW_REQUIRED",
        "需由另一名管理员执行已审批出款");
    var m = com.sujian.mall.common.Rows.required(memberMapper.lockAccountById(n(r, "member_id")));
    Problem.require(n(m, "debt") == 0 && !b(m, "blocked"), "RISK_REVIEW", "账户需风险复核");
    withdrawalMapper.updateForExecuteWithdrawal(actor, id);
    d.event(id + ":transfer", "TRANSFER", id);
    d.audit(actor, "WITHDRAW_EXECUTE", id, Map.of());
  }

  private void releaseWithdrawal(Map<String, Object> r, String state) {
    long uid = n(r, "member_id"), amount = n(r, "gross");
    String id = s(r, "id");
    shop.asset(uid, "reserved", -amount, id + ":release-reserved", null, "提现未出款解除冻结");
    shop.asset(uid, "available", amount, id + ":release-available", null, "提现金额退回可用");
    withdrawalMapper.updateForReleaseWithdrawal(state, id);
  }

  @Transactional
  public void transferResult(String id, Map<String, Object> result) {
    var r = com.sujian.mall.common.Rows.required(withdrawalMapper.findForReviewWithdrawal(id));
    if (List.of("SUCCESS", "FAILED", "REJECTED").contains(s(r, "state"))) return;
    Problem.require(
        List.of("SUBMITTED", "UNKNOWN", "CONFIRMING").contains(s(r, "state")),
        "WITHDRAW_STATE",
        "尚未授权出款");
    String state = s(result, "state");
    if (state.equals("SUCCESS")) {
      shop.asset(
          n(r, "member_id"), "reserved", -n(r, "gross"), id + ":paid", null, "渠道确认佣金出款（含税款预留）");
      withdrawalMapper.updateForTransferResult(s(result, "transfer_bill_no"), id);
    } else if (List.of("FAIL", "CANCELLED").contains(state)) releaseWithdrawal(r, "FAILED");
    else
      withdrawalMapper.updateForTransferResult2(
          state.equals("WAIT_USER_CONFIRM") ? "CONFIRMING" : "UNKNOWN",
          s(result, "package_info"),
          s(result, "transfer_bill_no"),
          id);
  }
}
