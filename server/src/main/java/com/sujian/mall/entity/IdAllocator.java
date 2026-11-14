package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for id_allocator. */
@TableName("id_allocator")
public class IdAllocator {
  @TableId(value = "scope_key", type = IdType.INPUT)
  @JsonProperty("scope_key")
  private String scopeKey;

  @TableField("next_id")
  @JsonProperty("next_id")
  private Long nextId;

  public String getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(String value) {
    this.scopeKey = value;
  }

  public Long getNextId() {
    return nextId;
  }

  public void setNextId(Long value) {
    this.nextId = value;
  }
}
