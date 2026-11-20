package com.sujian.mall.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RecordPageMapper {
  @SelectProvider(type=RecordPageSqlProvider.class, method="count")
  long count(Map<String, Object> parameters);
  @SelectProvider(type=RecordPageSqlProvider.class, method="rows")
  List<Map<String, Object>> rows(Map<String, Object> parameters);
  @SelectProvider(type=RecordPageSqlProvider.class, method="states")
  List<Map<String, Object>> states(Map<String, Object> parameters);

  @Select("SELECT id,product_id,name,code,price,cost,stock,held,version,active FROM sku WHERE product_id=#{productId} AND active=#{active} ORDER BY id")
  List<Map<String, Object>> productSkus(@Param("productId") long productId, @Param("active") boolean active);
  @Select("SELECT id,settlement_id,member_id,sales,amount,rank_no FROM settlement_award WHERE settlement_id=#{settlementId} ORDER BY rank_no,id")
  List<Map<String, Object>> awards(@Param("settlementId") long settlementId);
  @Select("SELECT id,order_id,sku_id,product_id,name,image,quantity,unit_price,cash_amount,points_amount,refunded_quantity,refunded_cash,refunded_points FROM order_item WHERE order_id=#{orderId} ORDER BY id")
  List<Map<String, Object>> orderItems(@Param("orderId") String orderId);
  @Select("SELECT COALESCE(SUM(amount),0) FROM commission WHERE beneficiary_id=#{memberId} AND state='PENDING'")
  long pendingCommission(@Param("memberId") long memberId);
  @Select("SELECT COUNT(*) FROM member WHERE inviter_id=#{memberId}")
  long directInviteCount(@Param("memberId") long memberId);
  @Select("SELECT COUNT(*) FROM member child JOIN member parent ON child.inviter_id=parent.id WHERE parent.inviter_id=#{memberId}")
  long secondInviteCount(@Param("memberId") long memberId);
}
