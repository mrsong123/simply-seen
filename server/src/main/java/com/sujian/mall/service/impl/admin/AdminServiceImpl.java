package com.sujian.mall.service.impl.admin;

import com.sujian.mall.service.AdminService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminServiceImpl implements AdminService {
  private final CatalogAdminOperations catalog;
  private final MemberAdminOperations member;
  private final OrderAdminOperations orders;
  private final FinanceAdminOperations finance;
  private final MarketingAdminOperations marketing;
  private final AccountAdminOperations accounts;

  public AdminServiceImpl(
      CatalogAdminOperations catalog,
      MemberAdminOperations member,
      OrderAdminOperations orders,
      FinanceAdminOperations finance,
      MarketingAdminOperations marketing,
      AccountAdminOperations accounts) {
    this.catalog = catalog;
    this.member = member;
    this.orders = orders;
    this.finance = finance;
    this.marketing = marketing;
    this.accounts = accounts;
  }

  @Override
  public Object categories() {
    return catalog.categories();
  }

  @Override
  public Object rules() {
    return catalog.rules();
  }

  @Override
  public Object adminProducts() {
    return catalog.adminProducts();
  }

  @Override
  public Object saveProduct(long actor, Map<String, Object> b) {
    return catalog.saveProduct(actor, b);
  }

  @Override
  public Object category(long actor, Map<String, Object> b) {
    return catalog.category(actor, b);
  }

  @Override
  public Object ledger(long uid) {
    return member.ledger(uid);
  }

  @Override
  public Object team(long uid) {
    return member.team(uid);
  }

  @Override
  public Object withdrawals(long uid) {
    return member.withdrawals(uid);
  }

  @Override
  public Object awards(long uid) {
    return member.awards(uid);
  }

  @Override
  public void deleteAddress(long id, long uid) {
    member.deleteAddress(id, uid);
  }

  @Override
  public Object members() {
    return member.members();
  }

  @Override
  public void block(long actor, long id, Map<String, Object> b) {
    member.block(actor, id, b);
  }

  @Override
  public void acceptPromoter(long uid, Map<String, Object> b) {
    member.acceptPromoter(uid, b);
  }

  @Override
  public Object payStatus(long uid, String no) {
    return orders.payStatus(uid, no);
  }

  @Override
  public void devPay(long uid, String no) {
    orders.devPay(uid, no);
  }

  @Override
  public Object summary() {
    return orders.summary();
  }

  @Override
  public Object adminOrders() {
    return orders.adminOrders();
  }

  @Override
  public Object adminOrder(String no) {
    return orders.adminOrder(no);
  }

  @Override
  public Object refunds() {
    return orders.refunds();
  }

  @Override
  public void reviewRefund(long actor, String id, Map<String, Object> b) {
    finance.reviewRefund(actor, id, b);
  }

  @Override
  public Object commissions() {
    return finance.commissions();
  }

  @Override
  public Object adminLedger() {
    return finance.adminLedger();
  }

  @Override
  public Object adminWithdrawals() {
    return finance.adminWithdrawals();
  }

  @Override
  public void reviewWithdraw(long actor, String id, Map<String, Object> b) {
    finance.reviewWithdraw(actor, id, b);
  }

  @Override
  public void execute(long actor, String id) {
    finance.execute(actor, id);
  }

  @Override
  public void queryTransfer(String id) {
    finance.queryTransfer(id);
  }

  @Override
  public void devTransfer(long actor, String id) {
    finance.devTransfer(actor, id);
  }

  @Override
  public Object reconciliation() {
    return finance.reconciliation();
  }

  @Override
  public Object reconcile(long actor) {
    return finance.reconcile(actor);
  }

  @Override
  public void resolve(long actor, long id, Map<String, Object> b) {
    finance.resolve(actor, id, b);
  }

  @Override
  public Object adminRules() {
    return marketing.adminRules();
  }

  @Override
  public void saveRule(long actor, Map<String, Object> b) {
    marketing.saveRule(actor, b);
  }

  @Override
  public void approveRule(long actor, long id) {
    marketing.approveRule(actor, id);
  }

  @Override
  public Object settlements() {
    return marketing.settlements();
  }

  @Override
  public Object settlement(long actor, Map<String, Object> b) {
    return marketing.settlement(actor, b);
  }

  @Override
  public void approveSettlement(long actor, long id) {
    marketing.approveSettlement(actor, id);
  }

  @Override
  public Object adminMe(long actor) {
    return accounts.adminMe(actor);
  }

  @Override
  public Object accounts() {
    return accounts.accounts();
  }

  @Override
  public void createAccount(long actor, Map<String, Object> b) {
    accounts.createAccount(actor, b);
  }

  @Override
  public void accountActive(long actor, long id, Map<String, Object> b) {
    accounts.accountActive(actor, id, b);
  }

  @Override
  public void resetPassword(long actor, long id, Map<String, Object> b) {
    accounts.resetPassword(actor, id, b);
  }

  @Override
  public Object audit() {
    return accounts.audit();
  }
}
