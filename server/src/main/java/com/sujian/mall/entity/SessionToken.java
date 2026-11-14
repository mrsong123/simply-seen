package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for session_token. */
@TableName("session_token")
public class SessionToken {
  @TableId(value = "token_hash", type = IdType.INPUT)
  @JsonProperty("token_hash")
  private String tokenHash;

  @TableField("actor_id")
  @JsonProperty("actor_id")
  private Long actorId;

  @TableField("kind")
  @JsonProperty("kind")
  private String kind;

  @TableField("expires_at")
  @JsonProperty("expires_at")
  private Long expiresAt;

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String value) {
    this.tokenHash = value;
  }

  public Long getActorId() {
    return actorId;
  }

  public void setActorId(Long value) {
    this.actorId = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    this.kind = value;
  }

  public Long getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Long value) {
    this.expiresAt = value;
  }
}
