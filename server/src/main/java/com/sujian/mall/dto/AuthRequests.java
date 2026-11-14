package com.sujian.mall.dto;

import jakarta.validation.constraints.*;

public final class AuthRequests {
  private AuthRequests() {}

  public record WechatLogin(@NotBlank String code, String inviteCode) implements RequestDto {}

  public record DevelopmentLogin(@NotBlank @Size(min = 2, max = 30) String name, String inviteCode)
      implements RequestDto {}

  public record AdminLogin(@NotBlank @Size(max = 80) String username, @NotBlank String password)
      implements RequestDto {}

  public record Profile(@NotBlank @Size(min = 2, max = 40) String name) implements RequestDto {}

  public record Password(
      @NotBlank String currentPassword, @NotBlank @Size(min = 12) String password)
      implements RequestDto {}

  public record ResetPassword(@NotBlank @Size(min = 12) String password) implements RequestDto {}

  public record Account(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{3,40}") String username,
      @NotBlank @Size(min = 12) String password,
      @NotBlank @Pattern(regexp = "ADMIN|OPERATOR|FINANCE") String role)
      implements RequestDto {}

  public record Acceptance(@NotNull Boolean accepted) implements RequestDto {}

  public record Active(@NotNull Boolean active) implements RequestDto {}

  public record Blocked(@NotNull Boolean blocked) implements RequestDto {}
}
