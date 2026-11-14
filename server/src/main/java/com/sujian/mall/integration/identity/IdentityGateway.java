package com.sujian.mall.integration.identity;

/** Name/identity-card consistency verification, without a liveness or face claim. */
public interface IdentityGateway {
  record Verification(boolean matched, String requestId, String reason, boolean development) {}

  Verification verify(String name, String idNumber);
}
