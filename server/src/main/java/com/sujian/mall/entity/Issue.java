package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for issue. */
@TableName("issue")
public class Issue {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("kind")
  @JsonProperty("kind")
  private String kind;

  @TableField("resource")
  @JsonProperty("resource")
  private String resource;

  @TableField("detail")
  @JsonProperty("detail")
  private String detail;

  @TableField("state")
  @JsonProperty("state")
  private String state;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    this.kind = value;
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

  public String getState() {
    return state;
  }

  public void setState(String value) {
    this.state = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
