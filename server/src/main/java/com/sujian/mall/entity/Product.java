package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for product. */
@TableName("product")
public class Product {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("category_id")
  @JsonProperty("category_id")
  private Long categoryId;

  @TableField("name")
  @JsonProperty("name")
  private String name;

  @TableField("subtitle")
  @JsonProperty("subtitle")
  private String subtitle;

  @TableField("description")
  @JsonProperty("description")
  private String description;

  @TableField("image")
  @JsonProperty("image")
  private String image;

  @TableField("color")
  @JsonProperty("color")
  private String color;

  @TableField("status")
  @JsonProperty("status")
  private String status;

  @TableField("featured")
  @JsonProperty("featured")
  private Boolean featured;

  @TableField("points_percent")
  @JsonProperty("points_percent")
  private Integer pointsPercent;

  @TableField("commission_percent")
  @JsonProperty("commission_percent")
  private Integer commissionPercent;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long value) {
    this.categoryId = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public void setSubtitle(String value) {
    this.subtitle = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String value) {
    this.image = value;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String value) {
    this.color = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  public Boolean getFeatured() {
    return featured;
  }

  public void setFeatured(Boolean value) {
    this.featured = value;
  }

  public Integer getPointsPercent() {
    return pointsPercent;
  }

  public void setPointsPercent(Integer value) {
    this.pointsPercent = value;
  }

  public Integer getCommissionPercent() {
    return commissionPercent;
  }

  public void setCommissionPercent(Integer value) {
    this.commissionPercent = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
