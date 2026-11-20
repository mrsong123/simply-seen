package com.sujian.mall.dto;

import jakarta.validation.constraints.*;

/** Validated, bounded server-side pagination and literal search. */
public class RecordPageQuery {
  @Min(1) @Max(1000000) private int page = 1;
  @Min(1) @Max(100) private int size = 20;
  @Size(max = 100) private String search = "";
  @Pattern(regexp = "[A-Z_]{0,40}") private String state = "";
  @Pattern(regexp = "[a-z_]{0,20}") private String asset = "";
  @Positive private Long categoryId;
  @Pattern(regexp = "curated|priceUp|priceDown") private String sort = "curated";
  private boolean points;

  public int getPage() { return page; }
  public void setPage(int value) { page = value; }
  public int getSize() { return size; }
  public void setSize(int value) { size = value; }
  public String getSearch() { return search == null ? "" : search.trim(); }
  public void setSearch(String value) { search = value; }
  public String getState() { return state == null ? "" : state; }
  public void setState(String value) { state = value; }
  public String getAsset() { return asset == null ? "" : asset; }
  public void setAsset(String value) { asset = value; }
  public Long getCategoryId() { return categoryId; }
  public void setCategoryId(Long value) { categoryId = value; }
  public boolean isPoints() { return points; }
  public void setPoints(boolean value) { points = value; }
  public String getSort() { return sort == null ? "curated" : sort; }
  public void setSort(String value) { sort = value; }
}
