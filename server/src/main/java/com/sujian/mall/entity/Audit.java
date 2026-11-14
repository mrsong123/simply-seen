package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for audit. */
@TableName("audit")
public class Audit {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("actor_id")
  @JsonProperty("actor_id")
  private Long actorId;

  @TableField("action")
  @JsonProperty("action")
  private String action;

  @TableField("resource")
  @JsonProperty("resource")
  private String resource;

  @TableField("detail")
  @JsonProperty("detail")
  private String detail;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getActorId() {
    return actorId;
  }

  public void setActorId(Long value) {
    this.actorId = value;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String value) {
    this.action = value;
  }

  public String getResource() {
    return resource;
  }

  public void setResource(String value) {
    this.resource = value;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String value) {
    this.detail = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
