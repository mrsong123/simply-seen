package com.sujian.mall.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Boundary DTOs convert to the existing application commands without leaking transport objects. */
public interface RequestDto {
  ObjectMapper COMMAND_MAPPER = new ObjectMapper();

  default Map<String, Object> toMap() {
    return COMMAND_MAPPER.convertValue(this, new TypeReference<Map<String, Object>>() {});
  }
}
