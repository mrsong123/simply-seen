package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for rule_version. */
@TableName("rule_version")
public class RuleVersion {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("state")
  @JsonProperty("state")
  private String state;

  @TableField("config")
  @JsonProperty("config")
  private String config;

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

  public String getState() {
    return state;
  }

  public void setState(String value) {
    this.state = value;
  }

  public String getConfig() {
    return config;
  }

  public void setConfig(String value) {
    this.config = value;
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
