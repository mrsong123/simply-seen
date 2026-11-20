package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Durable merchant-order prepayment intent; never serialized to member/admin projections. */
@TableName("payment_session")
public class PaymentSession {
  @TableId(value = "order_id", type = IdType.INPUT) private String orderId;
  private String channel;
  private String attemptId;
  private String state;
  @JsonIgnore private String responsePayload;
  private Long leaseUntil;
  private Long expiresAt;
  private Long updatedAt;
  public String getOrderId() { return orderId; }
  public void setOrderId(String value) { orderId = value; }
  public String getChannel() { return channel; }
  public void setChannel(String value) { channel = value; }
  public String getAttemptId() { return attemptId; }
  public void setAttemptId(String value) { attemptId = value; }
  public String getState() { return state; }
  public void setState(String value) { state = value; }
  public String getResponsePayload() { return responsePayload; }
  public void setResponsePayload(String value) { responsePayload = value; }
  public Long getLeaseUntil() { return leaseUntil; }
  public void setLeaseUntil(Long value) { leaseUntil = value; }
  public Long getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Long value) { expiresAt = value; }
  public Long getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Long value) { updatedAt = value; }
}
