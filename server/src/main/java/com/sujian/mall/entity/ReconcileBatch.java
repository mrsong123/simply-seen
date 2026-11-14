package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for reconcile_batch. */
@TableName("reconcile_batch")
public class ReconcileBatch {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("period_day")
  @JsonProperty("period_day")
  private String periodDay;

  @TableField("orders_total")
  @JsonProperty("orders_total")
  private Long ordersTotal;

  @TableField("refunds_total")
  @JsonProperty("refunds_total")
  private Long refundsTotal;

  @TableField("transfers_total")
  @JsonProperty("transfers_total")
  private Long transfersTotal;

  @TableField("diff_count")
  @JsonProperty("diff_count")
  private Integer diffCount;

  @TableField("created_at")
  @JsonProperty("created_at")
  private Long createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getPeriodDay() {
    return periodDay;
  }

  public void setPeriodDay(String value) {
    this.periodDay = value;
  }

  public Long getOrdersTotal() {
    return ordersTotal;
  }

  public void setOrdersTotal(Long value) {
    this.ordersTotal = value;
  }

  public Long getRefundsTotal() {
    return refundsTotal;
  }

  public void setRefundsTotal(Long value) {
    this.refundsTotal = value;
  }

  public Long getTransfersTotal() {
    return transfersTotal;
  }

  public void setTransfersTotal(Long value) {
    this.transfersTotal = value;
  }

  public Integer getDiffCount() {
    return diffCount;
  }

  public void setDiffCount(Integer value) {
    this.diffCount = value;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long value) {
    this.createdAt = value;
  }
}
