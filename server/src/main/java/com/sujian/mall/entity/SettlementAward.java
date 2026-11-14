package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for settlement_award. */
@TableName("settlement_award")
public class SettlementAward {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("settlement_id")
  @JsonProperty("settlement_id")
  private Long settlementId;

  @TableField("member_id")
  @JsonProperty("member_id")
  private Long memberId;

  @TableField("sales")
  @JsonProperty("sales")
  private Long sales;

  @TableField("amount")
  @JsonProperty("amount")
  private Long amount;

  @TableField("rank_no")
  @JsonProperty("rank_no")
  private Integer rankNo;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public Long getSettlementId() {
    return settlementId;
  }

  public void setSettlementId(Long value) {
    this.settlementId = value;
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long value) {
    this.memberId = value;
  }

  public Long getSales() {
    return sales;
  }

  public void setSales(Long value) {
    this.sales = value;
  }

  public Long getAmount() {
    return amount;
  }

  public void setAmount(Long value) {
    this.amount = value;
  }

  public Integer getRankNo() {
    return rankNo;
  }

  public void setRankNo(Integer value) {
    this.rankNo = value;
  }
}
