package com.sujian.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Persistent row for category. */
@TableName("category")
public class Category {
  @TableId(value = "id", type = IdType.INPUT)
  @JsonProperty("id")
  private Long id;

  @TableField("name")
  @JsonProperty("name")
  private String name;

  @TableField("sort_no")
  @JsonProperty("sort_no")
  private Integer sortNo;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public Integer getSortNo() {
    return sortNo;
  }

  public void setSortNo(Integer value) {
    this.sortNo = value;
  }
}
