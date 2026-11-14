package com.sujian.mall.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;

/** Shared event metadata and serialization. Persistence is performed by domain mappers. */
@Component
public class BusinessSupport {
  private final JsonCodec json;
  private final IdBlockAllocator allocator;
  private final com.sujian.mall.service.BusinessEventService events;

  public BusinessSupport(
      JsonCodec json,
      IdBlockAllocator allocator,
      com.sujian.mall.service.BusinessEventService events) {
    this.json = json;
    this.allocator = allocator;
    this.events = events;
  }

  public long id() {
    return allocator.next();
  }

  public long now() {
    return System.currentTimeMillis();
  }

  public String no(String prefix) {
    return prefix + id();
  }

  public String encode(Object value) {
    return json.encode(value);
  }

  public Map<String, Object> decode(String value) {
    return json.decode(value);
  }

  public List<Map<String, Object>> list(String value) {
    return json.list(value);
  }

  public static long n(Map<String, ?> m, String k) {
    Object v = m.get(k);
    return v == null ? 0 : Long.parseLong(v.toString());
  }

  public static String s(Map<String, ?> m, String k) {
    return Objects.toString(m.get(k), "");
  }

  public static boolean b(Map<String, ?> m, String k) {
    return Boolean.parseBoolean(s(m, k));
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public void audit(long actor, String action, String resource, Object detail) {
    events.audit(actor, action, resource, detail);
  }

  public void issue(String kind, String resource, String detail) {
    events.issue(kind, resource, detail);
  }

  public void event(String key, String kind, String aggregate) {
    events.event(key, kind, aggregate);
  }
}
