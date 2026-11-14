package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for commission. */
@TableName("commission")
public class Commission {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("order_id")
  @JsonProperty("order_id")
  private String orderId;

  @TableField("beneficiary_id")
  @JsonProperty("beneficiary_id")
  private Long beneficiaryId;

  @TableField("buyer_id")
  @JsonProperty("buyer_id")
  private Long buyerId;

  @TableField("level_no")
  @JsonProperty("level_no")
  private Integer levelNo;

  @TableField("amount")
  @JsonProperty("amount")
  private Long amount;

  @TableField("original_amount")
  @JsonProperty("original_amount")
  private Long originalAmount;

  @TableField("state")
  @JsonProperty("state")
  private String state;

  @TableField("rule_snapshot")
  @JsonProperty("rule_snapshot")
  private String ruleSnapshot;

  @TableField("settled_at")
  @JsonProperty("settled_at")
  private Long settledAt;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String value) {
    this.orderId = value;
  }

  public Long getBeneficiaryId() {
    return beneficiaryId;
  }

  public void setBeneficiaryId(Long value) {
    this.beneficiaryId = value;
  }

  public Long getBuyerId() {
    return buyerId;
  }

  public void setBuyerId(Long value) {
    this.buyerId = value;
  }

  public Integer getLevelNo() {
    return levelNo;
  }

  public void setLevelNo(Integer value) {
    this.levelNo = value;
  }

  public Long getAmount() {
    return amount;
  }

  public void setAmount(Long value) {
    this.amount = value;
  }

  public Long getOriginalAmount() {
    return originalAmount;
  }

  public void setOriginalAmount(Long value) {
    this.originalAmount = value;
  }

  public String getState() {
    return state;
  }

  public void setState(String value) {
    this.state = value;
  }

  public String getRuleSnapshot() {
    return ruleSnapshot;
  }

  public void setRuleSnapshot(String value) {
    this.ruleSnapshot = value;
  }

  public Long getSettledAt() {
    return settledAt;
  }

  public void setSettledAt(Long value) {
    this.settledAt = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
