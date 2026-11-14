package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for withdrawal. */
@TableName("withdrawal")
public class Withdrawal {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private String id;

  @TableField("member_id")
  @JsonProperty("member_id")
  private Long memberId;

  @TableField("gross")
  @JsonProperty("gross")
  private Long gross;

  @TableField("tax")
  @JsonProperty("tax")
  private Long tax;

  @TableField("net")
  @JsonProperty("net")
  private Long net;

  @TableField("state")
  @JsonProperty("state")
  private String state;

  @TableField("reviewer")
  @JsonProperty("reviewer")
  private Long reviewer;

  @TableField("executor")
  @JsonProperty("executor")
  private Long executor;

  @TableField("channel_id")
  @JsonProperty("channel_id")
  private String channelId;

  @TableField("package_info")
  @JsonProperty("package_info")
  private String packageInfo;

  @TableField("reason")
  @JsonProperty("reason")
  private String reason;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

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

  public Long getGross() {
    return gross;
  }

  public void setGross(Long value) {
    this.gross = value;
  }

  public Long getTax() {
    return tax;
  }

  public void setTax(Long value) {
    this.tax = value;
  }

  public Long getNet() {
    return net;
  }

  public void setNet(Long value) {
    this.net = value;
  }

  public String getState() {
    return state;
  }

  public void setState(String value) {
    this.state = value;
  }

  public Long getReviewer() {
    return reviewer;
  }

  public void setReviewer(Long value) {
    this.reviewer = value;
  }

  public Long getExecutor() {
    return executor;
  }

  public void setExecutor(Long value) {
    this.executor = value;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String value) {
    this.channelId = value;
  }

  public String getPackageInfo() {
    return packageInfo;
  }

  public void setPackageInfo(String value) {
    this.packageInfo = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
