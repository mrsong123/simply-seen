package com.sujian.mall.service;

import java.util.Map;

public interface AdminService {
  Object categories();

  Object rules();

  Object adminProducts();

  Object saveProduct(long actor, Map<String, Object> b);

  Object category(long actor, Map<String, Object> b);

  Object ledger(long uid);

  Object team(long uid);

  Object withdrawals(long uid);

  Object awards(long uid);

  void deleteAddress(long id, long uid);

  Object members();

  void block(long actor, long id, Map<String, Object> b);

  void acceptPromoter(long uid, Map<String, Object> b);

  Object payStatus(long uid, String no);

  void devPay(long uid, String no);

  Object summary();

  Object adminOrders();

  Object adminOrder(String no);

  Object refunds();

  void reviewRefund(long actor, String id, Map<String, Object> b);

  Object commissions();

  Object adminLedger();

  Object adminWithdrawals();

  void reviewWithdraw(long actor, String id, Map<String, Object> b);

  void execute(long actor, String id);

  void queryTransfer(String id);

  void devTransfer(long actor, String id);

  Object reconciliation();

  Object reconcile(long actor);

  void resolve(long actor, long id, Map<String, Object> b);

  Object adminRules();

  void saveRule(long actor, Map<String, Object> b);

  void approveRule(long actor, long id);

  Object settlements();

  Object settlement(long actor, Map<String, Object> b);

  void approveSettlement(long actor, long id);

  Object adminMe(long actor);

  Object accounts();

  void createAccount(long actor, Map<String, Object> b);

  void accountActive(long actor, long id, Map<String, Object> b);

  void resetPassword(long actor, long id, Map<String, Object> b);

  Object audit();
}
