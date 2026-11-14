package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for cart_item. */
@TableName("cart_item")
public class CartItem {
  // Composite primary key: use the dedicated Mapper methods instead of selectById/updateById.
  @TableField("member_id")
  @JsonProperty("member_id")
  private Long memberId;

  @TableField("sku_id")
  @JsonProperty("sku_id")
  private Long skuId;

  @TableField("quantity")
  @JsonProperty("quantity")
  private Integer quantity;

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long value) {
    this.memberId = value;
  }

  public Long getSkuId() {
    return skuId;
  }

  public void setSkuId(Long value) {
    this.skuId = value;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer value) {
    this.quantity = value;
  }
}
