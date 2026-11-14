package com.sujian.mall.service;

import java.util.*;

public interface WithdrawalService {
  Object withdraw(long uid, String key, Map<String, Object> b);

  void reviewWithdrawal(long actor, String id, Map<String, Object> body);

  void executeWithdrawal(long actor, String id);

  void transferResult(String id, Map<String, Object> result);
}
