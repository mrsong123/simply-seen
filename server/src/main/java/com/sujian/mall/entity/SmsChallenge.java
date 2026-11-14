package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@TableName("sms_challenge")
public class SmsChallenge {
  @TableId(type = IdType.INPUT) private String id;
  @JsonIgnore private String phoneHash;
  private String purpose;
  private Long memberId;
  @JsonIgnore private String codeHash;
  private String state;
  private Integer attempts;
  private Long expiresAt;
  private Long createdAt;
  private String providerRequestId;
  private Boolean development;

  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getPhoneHash() { return phoneHash; }
  public void setPhoneHash(String value) { phoneHash = value; }
  public String getPurpose() { return purpose; }
  public void setPurpose(String value) { purpose = value; }
  public Long getMemberId() { return memberId; }
  public void setMemberId(Long value) { memberId = value; }
  public String getCodeHash() { return codeHash; }
  public void setCodeHash(String value) { codeHash = value; }
  public String getState() { return state; }
  public void setState(String value) { state = value; }
  public Integer getAttempts() { return attempts; }
  public void setAttempts(Integer value) { attempts = value; }
  public Long getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Long value) { expiresAt = value; }
  public Long getCreatedAt() { return createdAt; }
  public void setCreatedAt(Long value) { createdAt = value; }
  public String getProviderRequestId() { return providerRequestId; }
  public void setProviderRequestId(String value) { providerRequestId = value; }
  public Boolean getDevelopment() { return development; }
  public void setDevelopment(Boolean value) { development = value; }
}
