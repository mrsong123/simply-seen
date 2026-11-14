package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@TableName("identity_attempt")
public class IdentityAttempt {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("member_id")
  private Long memberId;

  @TableField("state")
  private String state;

  @TableField("provider")
  private String provider;

  @TableField("provider_request_id")
  private String providerRequestId;

  @TableField("development")
  private Boolean development;

  @TableField("reason")
  private String reason;

  @TableField("created_at")
  private Long createdAt;

  @TableField("finished_at")
  private Long finishedAt;

  public String getId() { return id; }
  public void setId(String value) { id = value; }

  public Long getMemberId() { return memberId; }
  public void setMemberId(Long value) { memberId = value; }

  public String getState() { return state; }
  public void setState(String value) { state = value; }

  public String getProvider() { return provider; }
  public void setProvider(String value) { provider = value; }

  public String getProviderRequestId() { return providerRequestId; }
  public void setProviderRequestId(String value) { providerRequestId = value; }

  public Boolean getDevelopment() { return development; }
  public void setDevelopment(Boolean value) { development = value; }

  public String getReason() { return reason; }
  public void setReason(String value) { reason = value; }

  public Long getCreatedAt() { return createdAt; }
  public void setCreatedAt(Long value) { createdAt = value; }

  public Long getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Long value) { finishedAt = value; }

}
