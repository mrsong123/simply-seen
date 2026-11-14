package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for order_item. */
@TableName("order_item")
public class OrderItem {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("order_id")
  @JsonProperty("order_id")
  private String orderId;

  @TableField("sku_id")
  @JsonProperty("sku_id")
  private Long skuId;

  @TableField("product_id")
  @JsonProperty("product_id")
  private Long productId;

  @TableField("name")
  @JsonProperty("name")
  private String name;

  @TableField("image")
  @JsonProperty("image")
  private String image;

  @TableField("quantity")
  @JsonProperty("quantity")
  private Integer quantity;

  @TableField("unit_price")
  @JsonProperty("unit_price")
  private Long unitPrice;

  @TableField("cost")
  @JsonProperty("cost")
  private Long cost;

  @TableField("cash_amount")
  @JsonProperty("cash_amount")
  private Long cashAmount;

  @TableField("points_amount")
  @JsonProperty("points_amount")
  private Long pointsAmount;

  @TableField("commission_percent")
  @JsonProperty("commission_percent")
  private Integer commissionPercent;

  @TableField("refunded_quantity")
  @JsonProperty("refunded_quantity")
  private Integer refundedQuantity;

  @TableField("refunded_cash")
  @JsonProperty("refunded_cash")
  private Long refundedCash;

  @TableField("refunded_points")
  @JsonProperty("refunded_points")
  private Long refundedPoints;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String value) {
    this.orderId = value;
  }

  public Long getSkuId() {
    return skuId;
  }

  public void setSkuId(Long value) {
    this.skuId = value;
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

  public String getImage() {
    return image;
  }

  public void setImage(String value) {
    this.image = value;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer value) {
    this.quantity = value;
  }

  public Long getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(Long value) {
    this.unitPrice = value;
  }

  public Long getCost() {
    return cost;
  }

  public void setCost(Long value) {
    this.cost = value;
  }

  public Long getCashAmount() {
    return cashAmount;
  }

  public void setCashAmount(Long value) {
    this.cashAmount = value;
  }

  public Long getPointsAmount() {
    return pointsAmount;
  }

  public void setPointsAmount(Long value) {
    this.pointsAmount = value;
  }

  public Integer getCommissionPercent() {
    return commissionPercent;
  }

  public void setCommissionPercent(Integer value) {
    this.commissionPercent = value;
  }

  public Integer getRefundedQuantity() {
    return refundedQuantity;
  }

  public void setRefundedQuantity(Integer value) {
    this.refundedQuantity = value;
  }

  public Long getRefundedCash() {
    return refundedCash;
  }

  public void setRefundedCash(Long value) {
    this.refundedCash = value;
  }

  public Long getRefundedPoints() {
    return refundedPoints;
  }

  public void setRefundedPoints(Long value) {
    this.refundedPoints = value;
  }
}
