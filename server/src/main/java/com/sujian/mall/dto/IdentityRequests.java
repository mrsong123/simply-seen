package com.sujian.mall.dto;

import jakarta.validation.constraints.*;

public final class IdentityRequests {
  private IdentityRequests() {}

  public record Verification(
      @NotBlank @Size(min = 2, max = 40) String name,
      @NotBlank @Pattern(regexp = "[0-9]{17}[0-9Xx]") String idNumber,
      @NotNull Boolean accepted) {}
}
