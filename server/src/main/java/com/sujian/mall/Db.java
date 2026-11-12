package com.sujian.mall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Db {
  public final JdbcTemplate jdbc;
  public final ObjectMapper json;
  private long nextId = 0, limitId = 0;
  private final javax.sql.DataSource allocationSource;

  public Db(
      JdbcTemplate j,
      ObjectMapper o,
      org.springframework.boot.autoconfigure.jdbc.DataSourceProperties properties) {
    jdbc = j;
    json = o;
    allocationSource =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            properties.determineUrl(),
            properties.determineUsername(),
            properties.determinePassword());
  }

  public synchronized long id() {
    if (nextId >= limitId) {
      try (var connection = allocationSource.getConnection()) {
        connection.setAutoCommit(false);
        long base;
        try (var select =
                connection.prepareStatement(
                    "SELECT next_id FROM id_allocator WHERE scope_key='GLOBAL' FOR UPDATE");
            var row = select.executeQuery()) {
          if (!row.next()) throw new IllegalStateException("ID allocator missing");
          base = row.getLong(1);
        }
        try (var update =
            connection.prepareStatement(
                "UPDATE id_allocator SET next_id=? WHERE scope_key='GLOBAL'")) {
          update.setLong(1, base + 1000);
          update.executeUpdate();
        }
        connection.commit();
        nextId = base;
        limitId = base + 1000;
      } catch (java.sql.SQLException e) {
        throw new IllegalStateException("ID allocation failed", e);
      }
    }
    return nextId++;
  }

  public long now() {
    return System.currentTimeMillis();
  }

  public String no(String prefix) {
    return prefix + id();
  }

  public int exec(String s, Object... a) {
    return jdbc.update(s, a);
  }

  public List<Map<String, Object>> all(String s, Object... a) {
    return jdbc.query(
        s,
        (r, n) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          var md = r.getMetaData();
          for (int i = 1; i <= md.getColumnCount(); i++)
            m.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), r.getObject(i));
          return m;
        },
        a);
  }

  public Map<String, Object> one(String s, Object... a) {
    var list = all(s, a);
    if (list.isEmpty()) throw new Problem(404, "NOT_FOUND", "记录不存在或无权访问");
    return list.getFirst();
  }

  public long count(String s, Object... a) {
    return jdbc.queryForObject(s, Long.class, a);
  }

  public String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public Map<String, Object> decode(String s) {
    try {
      return json.readValue(s, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public List<Map<String, Object>> list(String s) {
    try {
      return json.readValue(s, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
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
    exec(
        "INSERT INTO audit VALUES(?,?,?,?,?,?)",
        id(),
        actor,
        action,
        resource,
        encode(detail),
        now());
  }

  public void issue(String kind, String resource, String detail) {
    if (count("SELECT COUNT(*) FROM issue WHERE kind=? AND resource=?", kind, resource) == 0)
      exec("INSERT INTO issue VALUES(?,?,?,?,?,?)", id(), kind, resource, detail, "OPEN", now());
  }

  public void event(String key, String kind, String aggregate) {
    if (count("SELECT COUNT(*) FROM outbox WHERE id=?", key) == 0)
      exec(
          "INSERT INTO outbox(id,kind,aggregate_id,state,next_at,created_at)"
              + " VALUES(?,?,?,'READY',?,?)",
          key,
          kind,
          aggregate,
          now(),
          now());
  }
}
