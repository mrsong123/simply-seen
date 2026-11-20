package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Persistent row for shop_order. */
@TableName("shop_order")
public class ShopOrder {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private String id;

  @TableField("member_id")
  @JsonProperty("member_id")
  private Long memberId;

  @TableField("state")
  @JsonProperty("state")
  private String state;

  @TableField("payment_state")
  @JsonProperty("payment_state")
  private String paymentState;

  @TableField("payment_channel")
  @JsonProperty("payment_channel")
  private String paymentChannel;

  @TableField("payment_payer_openid")
  @JsonIgnore
  private String paymentPayerOpenid;

  @TableField("payment_app_id")
  @JsonIgnore
  private String paymentAppId;

  @TableField("item_amount")
  @JsonProperty("item_amount")
  private Long itemAmount;

  @TableField("freight")
  @JsonProperty("freight")
  private Long freight;

  @TableField("points_used")
  @JsonProperty("points_used")
  private Long pointsUsed;

  @TableField("cash_due")
  @JsonProperty("cash_due")
  private Long cashDue;

  @TableField("cash_refunded")
  @JsonProperty("cash_refunded")
  private Long cashRefunded;

  @TableField("points_refunded")
  @JsonProperty("points_refunded")
  private Long pointsRefunded;

  @TableField("address_snapshot")
  @JsonProperty("address_snapshot")
  private String addressSnapshot;

  @TableField("rule_snapshot")
  @JsonProperty("rule_snapshot")
  private String ruleSnapshot;

  @TableField("remark")
  @JsonProperty("remark")
  private String remark;

  @TableField("transaction_id")
  @JsonProperty("transaction_id")
  private String transactionId;

  @TableField("expires_at")
  @JsonProperty("expires_at")
  private Long expiresAt;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  @TableField("paid_at")
  @JsonProperty("paid_at")
  private Long paidAt;

  @TableField("completed_at")
  @JsonProperty("completed_at")
  private Long completedAt;

  @TableField("carrier")
  @JsonProperty("carrier")
  private String carrier;

  @TableField("tracking_no")
  @JsonProperty("tracking_no")
  private String trackingNo;

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

  public String getState() {
    return state;
  }

  public void setState(String value) {
    this.state = value;
  }

  public String getPaymentState() {
    return paymentState;
  }

  public void setPaymentState(String value) {
    this.paymentState = value;
  }

  public String getPaymentChannel() { return paymentChannel; }
  public void setPaymentChannel(String value) { paymentChannel = value; }
  public String getPaymentPayerOpenid() { return paymentPayerOpenid; }
  public void setPaymentPayerOpenid(String value) { paymentPayerOpenid = value; }
  public String getPaymentAppId() { return paymentAppId; }
  public void setPaymentAppId(String value) { paymentAppId = value; }

  public Long getItemAmount() {
    return itemAmount;
  }

  public void setItemAmount(Long value) {
    this.itemAmount = value;
  }

  public Long getFreight() {
    return freight;
  }

  public void setFreight(Long value) {
    this.freight = value;
  }

  public Long getPointsUsed() {
    return pointsUsed;
  }

  public void setPointsUsed(Long value) {
    this.pointsUsed = value;
  }

  public Long getCashDue() {
    return cashDue;
  }

  public void setCashDue(Long value) {
    this.cashDue = value;
  }

  public Long getCashRefunded() {
    return cashRefunded;
  }

  public void setCashRefunded(Long value) {
    this.cashRefunded = value;
  }

  public Long getPointsRefunded() {
    return pointsRefunded;
  }

  public void setPointsRefunded(Long value) {
    this.pointsRefunded = value;
  }

  public String getAddressSnapshot() {
    return addressSnapshot;
  }

  public void setAddressSnapshot(String value) {
    this.addressSnapshot = value;
  }

  public String getRuleSnapshot() {
    return ruleSnapshot;
  }

  public void setRuleSnapshot(String value) {
    this.ruleSnapshot = value;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String value) {
    this.remark = value;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String value) {
    this.transactionId = value;
  }

  public Long getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Long value) {
    this.expiresAt = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }

  public Long getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Long value) {
    this.paidAt = value;
  }

  public Long getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Long value) {
    this.completedAt = value;
  }

  public String getCarrier() {
    return carrier;
  }

  public void setCarrier(String value) {
    this.carrier = value;
  }

  public String getTrackingNo() {
    return trackingNo;
  }

  public void setTrackingNo(String value) {
    this.trackingNo = value;
  }
}
