package com.sujian.mall.dto;

import jakarta.validation.constraints.*;

public final class PhoneRequests {
  private PhoneRequests() {}

  public record SendCode(@NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {}

  public record Login(
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{20,64}") String challengeId,
      @Size(max = 24) String inviteCode,
      @NotNull @AssertTrue Boolean accepted) {}

  public record BindPhone(
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{20,64}") String challengeId) {}

  public record BindWechat(@NotBlank @Size(max = 256) String code) {}
}
