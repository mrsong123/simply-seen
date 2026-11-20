package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for sku. */
@TableName("sku")
public class Sku {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("product_id")
  @JsonProperty("product_id")
  private Long productId;

  @TableField("name")
  @JsonProperty("name")
  private String name;

  @TableField("code")
  @JsonProperty("code")
  private String code;

  @TableField("price")
  @JsonProperty("price")
  private Long price;

  @TableField("cost")
  @JsonProperty("cost")
  private Long cost;

  @TableField("stock")
  @JsonProperty("stock")
  private Integer stock;

  @TableField("held")
  @JsonProperty("held")
  private Integer held;

  @TableField("version")
  @JsonProperty("version")
  private Integer version;

  @TableField("active")
  @JsonProperty("active")
  private Boolean active;

  public Boolean getActive() { return active; }

  public void setActive(Boolean value) { active = value; }

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long value) {
    this.productId = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String value) {
    this.code = value;
  }

  public Long getPrice() {
    return price;
  }

  public void setPrice(Long value) {
    this.price = value;
  }

  public Long getCost() {
    return cost;
  }

  public void setCost(Long value) {
    this.cost = value;
  }

  public Integer getStock() {
    return stock;
  }

  public void setStock(Integer value) {
    this.stock = value;
  }

  public Integer getHeld() {
    return held;
  }

  public void setHeld(Integer value) {
    this.held = value;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer value) {
    this.version = value;
  }
}
