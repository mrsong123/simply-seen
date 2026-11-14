package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for outbox. */
@TableName("outbox")
public class Outbox {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private String id;

  @TableField("kind")
  @JsonProperty("kind")
  private String kind;

  @TableField("aggregate_id")
  @JsonProperty("aggregate_id")
  private String aggregateId;

  @TableField("state")
  @JsonProperty("state")
  private String state;

  @TableField("attempts")
  @JsonProperty("attempts")
  private Integer attempts;

  @TableField("next_at")
  @JsonProperty("next_at")
  private Long nextAt;

  @TableField("last_error")
  @JsonProperty("last_error")
  private String lastError;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    this.kind = value;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public void setAggregateId(String value) {
    this.aggregateId = value;
  }

  public String getState() {
    return state;
  }

  public void setState(String value) {
    this.state = value;
  }

  public Integer getAttempts() {
    return attempts;
  }

  public void setAttempts(Integer value) {
    this.attempts = value;
  }

  public Long getNextAt() {
    return nextAt;
  }

  public void setNextAt(Long value) {
    this.nextAt = value;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String value) {
    this.lastError = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
