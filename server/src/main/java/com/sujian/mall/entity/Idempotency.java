package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for idempotency. */
@TableName("idempotency")
public class Idempotency {
  @TableId(value = "scope_key", type = IdType.INPUT)
  @JsonProperty("scope_key")
  private String scopeKey;

  @TableField("payload_hash")
  @JsonProperty("payload_hash")
  private String payloadHash;

  @TableField("response")
  @JsonProperty("response")
  private String response;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public String getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(String value) {
    this.scopeKey = value;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public void setPayloadHash(String value) {
    this.payloadHash = value;
  }

  public String getResponse() {
    return response;
  }

  public void setResponse(String value) {
    this.response = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
