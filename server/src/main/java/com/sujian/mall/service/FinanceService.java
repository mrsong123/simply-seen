package com.sujian.mall.service;

import java.util.*;

public interface FinanceService {
  Object refund(long uid, String key, Map<String, Object> b);

  void reviewRefund(long actor, String id, boolean approve, String reason);

  void refundSuccess(String id, String channelId);

  void settleCommissions();

  Object withdraw(long uid, String key, Map<String, Object> b);

  void reviewWithdrawal(long actor, String id, Map<String, Object> body);

  void executeWithdrawal(long actor, String id);

  void transferResult(String id, Map<String, Object> result);

  Object monthly(long actor, String month);

  void approveMonthly(long actor, long id);

  void saveRule(long actor, Map<String, Object> b);

  void approveRule(long actor, long id);
}
