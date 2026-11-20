package com.sujian.mall.mapper;

import java.util.Map;
import java.util.stream.Collectors;

/** Literal values are bound parameters; all SQL structure comes from a fixed resource definition. */
public final class RecordPageSqlProvider {
  public String count(Map<String, Object> parameters) {
    var source = source(parameters);
    return "SELECT COUNT(*) FROM " + source.from() + where(parameters, true);
  }

  public String rows(Map<String, Object> parameters) {
    var source = source(parameters);
    String sort = source.sort();
    if (source.catalog()) {
      if ("priceUp".equals(parameters.get("sort"))) sort = "price ASC,t.id";
      if ("priceDown".equals(parameters.get("sort"))) sort = "price DESC,t.id";
    }
    return "SELECT " + source.columns() + " FROM " + source.from() + where(parameters, true)
        + " ORDER BY " + sort + " LIMIT #{size} OFFSET #{offset}";
  }

  public String states(Map<String, Object> parameters) {
    var source = source(parameters);
    return "SELECT DISTINCT " + source.state() + " AS record_state FROM " + source.from()
        + where(parameters, false) + " ORDER BY record_state LIMIT 100";
  }

  private RecordPageSource source(Map<String, Object> parameters) {
    return (RecordPageSource) parameters.get("source");
  }

  private String where(Map<String, Object> parameters, boolean filterState) {
    var source = source(parameters);
    StringBuilder sql = new StringBuilder(" WHERE 1=1");
    if (!source.scope().isEmpty()) sql.append(" AND (").append(source.scope()).append(')');
    if (parameters.get("term") != null) {
      sql.append(" AND (").append(source.search().stream()
          .map(column -> "CAST(" + column + " AS CHAR(1024)) LIKE #{term} ESCAPE '!' ")
          .collect(Collectors.joining(" OR "))).append(')');
    }
    String state = (String) parameters.get("state");
    if (filterState && state != null && !state.isEmpty()) sql.append(" AND (").append(source.state()).append(")=#{state}");
    String asset = (String) parameters.get("asset");
    if (asset != null && !asset.isEmpty()) sql.append(" AND t.asset=#{asset}");
    if ((source.catalog() || source.kind().equals("products")) && parameters.get("categoryId") != null)
      sql.append(" AND t.category_id=#{categoryId}");
    if (source.catalog() && Boolean.TRUE.equals(parameters.get("points"))) sql.append(" AND t.points_percent>0");
    return sql.toString();
  }
}
