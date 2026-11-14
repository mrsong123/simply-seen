package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for address. */
@TableName("address")
public class Address {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("member_id")
  @JsonProperty("member_id")
  private Long memberId;

  @TableField("name")
  @JsonProperty("name")
  private String name;

  @TableField("phone")
  @JsonProperty("phone")
  private String phone;

  @TableField("region")
  @JsonProperty("region")
  private String region;

  @TableField("detail")
  @JsonProperty("detail")
  private String detail;

  @TableField("is_default")
  @JsonProperty("is_default")
  private Boolean isDefault;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long value) {
    this.memberId = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String value) {
    this.phone = value;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String value) {
    this.region = value;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String value) {
    this.detail = value;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean value) {
    this.isDefault = value;
  }
}
