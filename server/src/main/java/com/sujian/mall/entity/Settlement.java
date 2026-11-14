package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for settlement. */
@TableName("settlement")
public class Settlement {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("period")
  @JsonProperty("period")
  private String period;

  @TableField("state")
  @JsonProperty("state")
  private String state;

  @TableField("budget")
  @JsonProperty("budget")
  private Long budget;

  @TableField("creator")
  @JsonProperty("creator")
  private Long creator;

  @TableField("reviewer")
  @JsonProperty("reviewer")
  private Long reviewer;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getPeriod() {
    return period;
  }

  public void setPeriod(String value) {
    this.period = value;
  }

  public String getState() {
    return state;
  }

  public void setState(String value) {
    this.state = value;
  }

  public Long getBudget() {
    return budget;
  }

  public void setBudget(Long value) {
    this.budget = value;
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

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
