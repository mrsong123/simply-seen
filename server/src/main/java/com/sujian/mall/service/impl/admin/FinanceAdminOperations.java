package com.sujian.mall.service.impl.admin;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.BusinessSupport;
import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.job.OutboxJob;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.FinanceService;
import com.sujian.mall.service.ReconciliationService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceAdminOperations {
  @org.springframework.beans.factory.annotation.Autowired private CommissionMapper commissionMapper;
  @org.springframework.beans.factory.annotation.Autowired private IssueMapper issueMapper;
  @org.springframework.beans.factory.annotation.Autowired private LedgerMapper ledgerMapper;
  @org.springframework.beans.factory.annotation.Autowired private OutboxMapper outboxMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private ReconcileBatchMapper reconcileBatchMapper;

  @org.springframework.beans.factory.annotation.Autowired private WithdrawalMapper withdrawalMapper;

  private final BusinessSupport d;
  private final Settings c;
  private final FinanceService f;
  private final OutboxJob jobs;
  private final ReconciliationService reconciliation;

  public FinanceAdminOperations(
      BusinessSupport d,
      Settings c,
      FinanceService f,
      OutboxJob jobs,
      ReconciliationService reconciliation) {
    this.d = d;
    this.c = c;
    this.f = f;
    this.jobs = jobs;
    this.reconciliation = reconciliation;
  }

  public void reviewRefund(long actor, String id, Map<String, Object> b) {
    f.reviewRefund(actor, id, b(b, "approve"), s(b, "reason"));
    jobs.run();
  }

  public Object commissions() {
    return commissionMapper.listForCommissions();
  }

  public Object adminLedger() {
    return ledgerMapper.listForAdminLedger();
  }

  public Object adminWithdrawals() {
    return withdrawalMapper.listForAdminWithdrawals();
  }

  public void reviewWithdraw(long actor, String id, Map<String, Object> b) {
    f.reviewWithdrawal(actor, id, b);
  }

  public void execute(long actor, String id) {
    f.executeWithdrawal(actor, id);
    jobs.run();
  }

  public void queryTransfer(String id) {
    jobs.transfer(id, 1);
  }

  @Transactional
  public void devTransfer(long actor, String id) {
    Problem.require(c.dev(), "DEV_DISABLED", "仅开发环境允许测试出款");
    f.transferResult(id, Map.of("state", "SUCCESS", "transfer_bill_no", "DEV-" + id));
    d.audit(actor, "DEV_TRANSFER", id, Map.of());
  }

  public Object reconciliation() {
    return Map.of(
        "batches",
        reconcileBatchMapper.listForReconciliation(),
        "issues",
        issueMapper.listForReconciliation(),
        "outbox",
        outboxMapper.listForReconciliation());
  }

  public Object reconcile(long actor) {
    return reconciliation.reconcile(actor);
  }

  @Transactional
  public void resolve(long actor, long id, Map<String, Object> b) {
    Problem.require(s(b, "reason").length() >= 4, "REASON_REQUIRED", "请填写实际处理依据，至少4字");
    issueMapper.updateForResolve(id);
    d.audit(actor, "ISSUE_RESOLVE", "" + id, b);
  }
}
