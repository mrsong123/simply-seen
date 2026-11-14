package com.sujian.mall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class TradeRequests {
  private TradeRequests() {}

  public record Quantity(@NotNull @Min(0) @Max(99) Integer quantity) implements RequestDto {}

  public record QuoteItem(
      @NotNull @Positive Long skuId, @NotNull @Min(1) @Max(99) Integer quantity) {}

  public record Quote(
      @NotEmpty @Size(max = 50) List<@Valid QuoteItem> items,
      @NotNull @Positive Long addressId,
      @NotNull @Min(0) Long pointsRequested)
      implements RequestDto {}

  public record CreateOrder(
      @NotBlank String quoteId,
      @NotNull @Positive Long addressId,
      @Size(max = 240) String buyerRemark)
      implements RequestDto {}

  public record RefundItem(
      @NotNull @Positive Long orderItemId, @NotNull @Min(1) Integer quantity) {}

  public record Refund(
      @NotBlank String orderNo,
      @NotBlank @Size(min = 2, max = 240) String reason,
      @NotEmpty List<@Valid RefundItem> lines)
      implements RequestDto {}

  public record Withdrawal(@NotNull @Positive Long amountFen) implements RequestDto {}

  public record Shipment(
      @NotBlank @Size(max = 80) String carrier,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{5,80}") String trackingNo)
      implements RequestDto {}
}
