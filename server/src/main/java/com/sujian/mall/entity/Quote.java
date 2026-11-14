package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for quote. */
@TableName("quote")
public class Quote {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private String id;

  @TableField("member_id")
  @JsonProperty("member_id")
  private Long memberId;

  @TableField("address_id")
  @JsonProperty("address_id")
  private Long addressId;

  @TableField("payload")
  @JsonProperty("payload")
  private String payload;

  @TableField("expires_at")
  @JsonProperty("expires_at")
  private Long expiresAt;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long value) {
    this.memberId = value;
  }

  public Long getAddressId() {
    return addressId;
  }

  public void setAddressId(Long value) {
    this.addressId = value;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String value) {
    this.payload = value;
  }

  public Long getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Long value) {
    this.expiresAt = value;
  }
}
