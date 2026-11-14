package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for ledger. */
@TableName("ledger")
public class Ledger {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("member_id")
  @JsonProperty("member_id")
  private Long memberId;

  @TableField("event_key")
  @JsonProperty("event_key")
  private String eventKey;

  @TableField("asset")
  @JsonProperty("asset")
  private String asset;

  @TableField("amount")
  @JsonProperty("amount")
  private Long amount;

  @TableField("balance")
  @JsonProperty("balance")
  private Long balance;

  @TableField("order_id")
  @JsonProperty("order_id")
  private String orderId;

  @TableField("note")
  @JsonProperty("note")
  private String note;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long value) {
    this.memberId = value;
  }

  public String getEventKey() {
    return eventKey;
  }

  public void setEventKey(String value) {
    this.eventKey = value;
  }

  public String getAsset() {
    return asset;
  }

  public void setAsset(String value) {
    this.asset = value;
  }

  public Long getAmount() {
    return amount;
  }

  public void setAmount(Long value) {
    this.amount = value;
  }

  public Long getBalance() {
    return balance;
  }

  public void setBalance(Long value) {
    this.balance = value;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String value) {
    this.orderId = value;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String value) {
    this.note = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
