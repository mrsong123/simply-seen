package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@TableName("member_phone")
public class MemberPhone {
  @TableId(value = "member_id", type = IdType.INPUT)
  private Long memberId;
  @JsonIgnore private String phoneHash;
  @JsonIgnore private String phoneCiphertext;
  private String phoneMasked;
  private Long createdAt;

  public Long getMemberId() { return memberId; }
  public void setMemberId(Long value) { memberId = value; }
  public String getPhoneHash() { return phoneHash; }
  public void setPhoneHash(String value) { phoneHash = value; }
  public String getPhoneCiphertext() { return phoneCiphertext; }
  public void setPhoneCiphertext(String value) { phoneCiphertext = value; }
  public String getPhoneMasked() { return phoneMasked; }
  public void setPhoneMasked(String value) { phoneMasked = value; }
  public Long getCreatedAt() { return createdAt; }
  public void setCreatedAt(Long value) { createdAt = value; }
}
