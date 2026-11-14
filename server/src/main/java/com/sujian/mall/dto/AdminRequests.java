package com.sujian.mall.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class AdminRequests {
  private AdminRequests() {}

  public record Category(
      Long id, @NotBlank @Size(max = 60) String name, @JsonProperty("sort_no") Long sortNo)
      implements RequestDto {}

  public record Review(
      @NotNull Boolean approve, @Min(0) Long taxFen, @Size(max = 240) String reason)
      implements RequestDto {}

  public record Reason(@NotBlank @Size(min = 4, max = 240) String reason) implements RequestDto {}

  public record Month(@NotBlank @Pattern(regexp = "[0-9]{4}-[0-9]{2}") String period)
      implements RequestDto {}

  public record Sku(
      Long id,
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 80) String code,
      @NotNull @Positive Long price,
      @NotNull @Min(0) Long cost,
      @NotNull @Min(0) @Max(1000000) Integer stock) {}

  public record Product(
      Long id,
      @JsonProperty("category_id") @NotNull @Positive Long categoryId,
      @NotBlank @Size(max = 120) String name,
      @Size(max = 240) String subtitle,
      @NotBlank @Size(max = 20000) String description,
      @NotBlank @Size(max = 1000) String image,
      @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
      @NotBlank @Pattern(regexp = "ON_SALE|OFF_SALE") String status,
      Boolean featured,
      @JsonProperty("points_percent") @NotNull @Min(0) @Max(100) Integer pointsPercent,
      @JsonProperty("commission_percent") @NotNull @Min(0) @Max(10) Integer commissionPercent,
      @NotEmpty @Size(max = 20) List<@Valid Sku> skus)
      implements RequestDto {}

  public record Rule(
      @NotNull @Min(0) Long level1Bps,
      @NotNull @Min(0) Long level2Bps,
      @NotNull @Min(0) @Max(50) Long marginSharePercent,
      @NotNull @Min(7) @Max(90) Long settlementDays,
      @NotNull @Min(0) @Max(100) Long pointsPer100Yuan,
      @NotNull @Min(0) Long freightFen,
      @NotNull @Min(0) Long freeShippingFen,
      @NotNull @Min(0) Long monthlyBudgetFen,
      @NotNull @Min(0) @Max(50000) Long monthlyCapFen,
      @NotNull @Min(0) @Max(1000) Long monthlyPoolBps,
      @NotNull @Min(0) Long commissionBudgetFen,
      @NotNull Boolean distributionEnabled)
      implements RequestDto {}
}
