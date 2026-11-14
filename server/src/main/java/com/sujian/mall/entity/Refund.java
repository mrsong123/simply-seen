package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for refund. */
@TableName("refund")
public class Refund {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private String id;

  @TableField("order_id")
  @JsonProperty("order_id")
  private String orderId;

  @TableField("member_id")
  @JsonProperty("member_id")
  private Long memberId;

  @TableField("state")
  @JsonProperty("state")
  private String state;

  @TableField("amount")
  @JsonProperty("amount")
  private Long amount;

  @TableField("points")
  @JsonProperty("points")
  private Long points;

  @TableField("line_snapshot")
  @JsonProperty("line_snapshot")
  private String lineSnapshot;

  @TableField("reason")
  @JsonProperty("reason")
  private String reason;

  @TableField("creator")
  @JsonProperty("creator")
  private Long creator;

  @TableField("reviewer")
  @JsonProperty("reviewer")
  private Long reviewer;

  @TableField("channel_id")
  @JsonProperty("channel_id")
  private String channelId;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String value) {
    this.orderId = value;
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

  public Long getAmount() {
    return amount;
  }

  public void setAmount(Long value) {
    this.amount = value;
  }

  public Long getPoints() {
    return points;
  }

  public void setPoints(Long value) {
    this.points = value;
  }

  public String getLineSnapshot() {
    return lineSnapshot;
  }

  public void setLineSnapshot(String value) {
    this.lineSnapshot = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }

  public Long getCreator() {
    return creator;
  }

  public void setCreator(Long value) {
    this.creator = value;
  }

  public Long getReviewer() {
    return reviewer;
  }

  public void setReviewer(Long value) {
    this.reviewer = value;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String value) {
    this.channelId = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
