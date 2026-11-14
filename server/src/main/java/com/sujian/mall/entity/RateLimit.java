package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for rate_limit. */
@TableName("rate_limit")
public class RateLimit {
  @TableId(value = "bucket", type = IdType.INPUT)
  @JsonProperty("bucket")
  private String bucket;

  @TableField("hits")
  @JsonProperty("hits")
  private Integer hits;

  @TableField("window_at")
  @JsonProperty("window_at")
  private Long windowAt;

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String value) {
    this.bucket = value;
  }

  public Integer getHits() {
    return hits;
  }

  public void setHits(Integer value) {
    this.hits = value;
  }

  public Long getWindowAt() {
    return windowAt;
  }

  public void setWindowAt(Long value) {
    this.windowAt = value;
  }
}
