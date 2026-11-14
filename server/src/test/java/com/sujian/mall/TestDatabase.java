package com.sujian.mall;

import com.sujian.mall.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only direct database fixture and assertion access. */
final class TestDatabase {
  final JdbcTemplate jdbc;
  final BusinessSupport support;

  TestDatabase(JdbcTemplate jdbc, BusinessSupport support) {
    this.jdbc = jdbc;
    this.support = support;
  }

  long id() {
    return support.id();
  }

  long now() {
    return support.now();
  }

  String encode(Object value) {
    return support.encode(value);
  }

  Map<String, Object> decode(String value) {
    return support.decode(value);
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
}
