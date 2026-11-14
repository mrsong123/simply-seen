package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for member. */
@TableName("member")
public class Member {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("openid")
  @JsonProperty("openid")
  private String openid;

  @TableField("name")
  @JsonProperty("name")
  private String name;

  @TableField("invite_code")
  @JsonProperty("invite_code")
  private String inviteCode;

  @TableField("inviter_id")
  @JsonProperty("inviter_id")
  private Long inviterId;

  @TableField("points")
  @JsonProperty("points")
  private Long points;

  @TableField("points_held")
  @JsonProperty("points_held")
  private Long pointsHeld;

  @TableField("available")
  @JsonProperty("available")
  private Long available;

  @TableField("reserved")
  @JsonProperty("reserved")
  private Long reserved;

  @TableField("debt")
  @JsonProperty("debt")
  private Long debt;

  @TableField("promoter")
  @JsonProperty("promoter")
  private Boolean promoter;

  @TableField("blocked")
  @JsonProperty("blocked")
  private Boolean blocked;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getOpenid() {
    return openid;
  }

  public void setOpenid(String value) {
    this.openid = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getInviteCode() {
    return inviteCode;
  }

  public void setInviteCode(String value) {
    this.inviteCode = value;
  }

  public Long getInviterId() {
    return inviterId;
  }

  public void setInviterId(Long value) {
    this.inviterId = value;
  }

  public Long getPoints() {
    return points;
  }

  public void setPoints(Long value) {
    this.points = value;
  }

  public Long getPointsHeld() {
    return pointsHeld;
  }

  public void setPointsHeld(Long value) {
    this.pointsHeld = value;
  }

  public Long getAvailable() {
    return available;
  }

  public void setAvailable(Long value) {
    this.available = value;
  }

  public Long getReserved() {
    return reserved;
  }

  public void setReserved(Long value) {
    this.reserved = value;
  }

  public Long getDebt() {
    return debt;
  }

  public void setDebt(Long value) {
    this.debt = value;
  }

  public Boolean getPromoter() {
    return promoter;
  }

  public void setPromoter(Boolean value) {
    this.promoter = value;
  }

  public Boolean getBlocked() {
    return blocked;
  }

  public void setBlocked(Boolean value) {
    this.blocked = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
