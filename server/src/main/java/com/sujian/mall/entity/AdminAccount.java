package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for admin_account. */
@TableName("admin_account")
public class AdminAccount {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("username")
  @JsonProperty("username")
  private String username;

  @TableField("password_hash")
  @JsonProperty("password_hash")
  private String passwordHash;

  @TableField("role")
  @JsonProperty("role")
  private String role;

  @TableField("active")
  @JsonProperty("active")
  private Boolean active;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String value) {
    this.username = value;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String value) {
    this.passwordHash = value;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String value) {
    this.role = value;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean value) {
    this.active = value;
  }
}
