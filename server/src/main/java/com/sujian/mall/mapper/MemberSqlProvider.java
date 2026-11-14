package com.sujian.mall.mapper;

import java.util.Map;
import java.util.Set;

/** Restricted column selection for the five independently maintained asset balances. */
public final class MemberSqlProvider {
  public String updateAsset(Map<String, Object> parameters) {
    String asset = String.valueOf(parameters.get("asset"));
    if (!Set.of("points", "points_held", "available", "reserved", "debt").contains(asset))
      throw new IllegalArgumentException("Unsupported asset column");
    return "UPDATE member SET " + asset + "=#{balance} WHERE id=#{memberId}";
  }
}
