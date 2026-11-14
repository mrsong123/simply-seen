package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for system_guard. */
@TableName("system_guard")
public class SystemGuard {
  @TableId(value = "scope_key", type = IdType.INPUT)
  @JsonProperty("scope_key")
  private String scopeKey;

  public String getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(String value) {
    this.scopeKey = value;
  }
}
