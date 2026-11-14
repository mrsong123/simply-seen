package com.sujian.mall.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** JSON serialization at persistence and external-channel boundaries. */
@Component
public class JsonCodec {
  private final ObjectMapper mapper;

  public JsonCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String encode(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot serialize business data", e);
    }
  }

  public Map<String, Object> decode(String value) {
    try {
      return mapper.readValue(value, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid stored business data", e);
    }
  }

  public List<Map<String, Object>> list(String value) {
    try {
      return mapper.readValue(value, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid stored business lines", e);
    }
  }
}
