package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@TableName("member_identity")
public class MemberIdentity {
  @TableId(value = "member_id", type = IdType.INPUT)
  private Long memberId;

  @TableField("state")
  private String state;

  @TableField("credential_hash")
  @JsonIgnore
  private String credentialHash;

  @TableField("id_number_hash")
  @JsonIgnore
  private String idNumberHash;

  @TableField(value = "verified_id_hash", updateStrategy = FieldStrategy.ALWAYS)
  @JsonIgnore
  private String verifiedIdHash;

  @TableField("name_ciphertext")
  @JsonIgnore
  private String nameCiphertext;

  @TableField("id_number_ciphertext")
  @JsonIgnore
  private String idNumberCiphertext;

  @TableField("name_masked")
  private String nameMasked;

  @TableField("id_number_masked")
  private String idNumberMasked;

  @TableField("provider")
  private String provider;

  @TableField("provider_request_id")
  private String providerRequestId;

  @TableField("development")
  private Boolean development;

  @TableField(value = "verified_at", updateStrategy = FieldStrategy.ALWAYS)
  private Long verifiedAt;

  @TableField("rejection_reason")
  private String rejectionReason;

  @TableField("active_attempt_id")
  private String activeAttemptId;

  @TableField("last_attempt_at")
  private Long lastAttemptAt;

  @TableField("attempt_day")
  private Long attemptDay;

  @TableField("attempt_count")
  private Integer attemptCount;

  @TableField("consent_version")
  private String consentVersion;

  @TableField("consent_at")
  private Long consentAt;

  @TableField("created_at")
  private Long createdAt;

  @TableField("updated_at")
  private Long updatedAt;

  public Long getMemberId() { return memberId; }
  public void setMemberId(Long value) { memberId = value; }

  public String getState() { return state; }
  public void setState(String value) { state = value; }

  @JsonIgnore
  public String getCredentialHash() { return credentialHash; }
  public void setCredentialHash(String value) { credentialHash = value; }

  @JsonIgnore
  public String getIdNumberHash() { return idNumberHash; }
  public void setIdNumberHash(String value) { idNumberHash = value; }

  @JsonIgnore
  public String getVerifiedIdHash() { return verifiedIdHash; }
  public void setVerifiedIdHash(String value) { verifiedIdHash = value; }

  @JsonIgnore
  public String getNameCiphertext() { return nameCiphertext; }
  public void setNameCiphertext(String value) { nameCiphertext = value; }

  @JsonIgnore
  public String getIdNumberCiphertext() { return idNumberCiphertext; }
  public void setIdNumberCiphertext(String value) { idNumberCiphertext = value; }

  public String getNameMasked() { return nameMasked; }
  public void setNameMasked(String value) { nameMasked = value; }

  public String getIdNumberMasked() { return idNumberMasked; }
  public void setIdNumberMasked(String value) { idNumberMasked = value; }

  public String getProvider() { return provider; }
  public void setProvider(String value) { provider = value; }

  public String getProviderRequestId() { return providerRequestId; }
  public void setProviderRequestId(String value) { providerRequestId = value; }

  public Boolean getDevelopment() { return development; }
  public void setDevelopment(Boolean value) { development = value; }

  public Long getVerifiedAt() { return verifiedAt; }
  public void setVerifiedAt(Long value) { verifiedAt = value; }

  public String getRejectionReason() { return rejectionReason; }
  public void setRejectionReason(String value) { rejectionReason = value; }

  public String getActiveAttemptId() { return activeAttemptId; }
  public void setActiveAttemptId(String value) { activeAttemptId = value; }

  public Long getLastAttemptAt() { return lastAttemptAt; }
  public void setLastAttemptAt(Long value) { lastAttemptAt = value; }

  public Long getAttemptDay() { return attemptDay; }
  public void setAttemptDay(Long value) { attemptDay = value; }

  public Integer getAttemptCount() { return attemptCount; }
  public void setAttemptCount(Integer value) { attemptCount = value; }

  public String getConsentVersion() { return consentVersion; }
  public void setConsentVersion(String value) { consentVersion = value; }

  public Long getConsentAt() { return consentAt; }
  public void setConsentAt(Long value) { consentAt = value; }

  public Long getCreatedAt() { return createdAt; }
  public void setCreatedAt(Long value) { createdAt = value; }

  public Long getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Long value) { updatedAt = value; }

}
