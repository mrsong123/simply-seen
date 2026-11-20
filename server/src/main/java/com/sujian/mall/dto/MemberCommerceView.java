package com.sujian.mall.dto;

import java.util.*;

/** HTTP projections for members; retained financial snapshots stay in the database and services. */
public final class MemberCommerceView {
  private static final Set<String> PUBLIC_RULE = Set.of("version", "distributionEnabled",
      "pointsPer100Yuan", "freightFen", "freeShippingFen", "settlementDays", "level1Bps", "level2Bps");
  private MemberCommerceView() {}

  public static Map<String, Object> quote(Map<String, Object> original) {
    var result = copy(original);
    result.put("items", rows(original.get("items"), Set.of("cost", "commissionPercent")));
    var rule = copy(original.get("rule"));
    rule.keySet().retainAll(PUBLIC_RULE);
    result.put("rule", rule);
    return result;
  }

  public static Map<String, Object> order(Object original) {
    var result = without(original, Set.of("payment_payer_openid", "payment_app_id", "rule_snapshot", "address_snapshot"));
    result.put("items", rows(result.get("items"), Set.of("cost", "commission_percent")));
    if (result.containsKey("refunds")) result.put("refunds", rows(result.get("refunds"), Set.of("line_snapshot")));
    return result;
  }

  public static List<Map<String, Object>> orders(List<Map<String, Object>> originals) {
    return originals.stream().map(MemberCommerceView::order).toList();
  }

  public static Map<String, Object> team(Object original) {
    var result = copy(original);
    result.put("commissions", rows(result.get("commissions"), Set.of("rule_snapshot")));
    return result;
  }

  private static List<Map<String, Object>> rows(Object values, Set<String> excluded) {
    if (!(values instanceof List<?> list)) throw new IllegalStateException("Invalid member commerce collection");
    return list.stream().map(value -> without(value, excluded)).toList();
  }

  private static Map<String, Object> without(Object original, Set<String> excluded) {
    var result = copy(original);
    excluded.forEach(result::remove);
    return result;
  }

  private static Map<String, Object> copy(Object original) {
    if (!(original instanceof Map<?, ?> source)) throw new IllegalStateException("Invalid member commerce projection");
    var result = new LinkedHashMap<String, Object>();
    source.forEach((key, value) -> result.put(key.toString(), value));
    return result;
  }
}
