package com.sujian.mall.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record AddressRequest(
    Long id,
    @NotBlank @Size(max = 60) String name,
    @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
    @NotBlank @Size(max = 160) String region,
    @NotBlank @Size(max = 240) String detail,
    @JsonProperty("is_default") Boolean isDefault)
    implements RequestDto {}
