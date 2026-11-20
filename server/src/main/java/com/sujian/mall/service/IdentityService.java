package com.sujian.mall.service;

import java.util.Map;

public interface IdentityService {
  Map<String, Object> status(long memberId);

  Map<String, Object> verify(long memberId, String name, String idNumber, boolean accepted);

  void requireVerifiedForWithdrawal(long memberId);

  /** Backend-only verified name for encrypted payment-channel submission. */
  String verifiedNameForTransfer(long memberId);
}
