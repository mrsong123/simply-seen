package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Ledger;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LedgerMapper extends BaseMapper<Ledger> {

  @Select(
      "SELECT * FROM ledger WHERE member_id=#{memberId} ORDER BY created_at DESC,id DESC LIMIT 200")
  List<Map<String, Object>> listForLedger(@Param("memberId") long memberId);

  @Select("SELECT * FROM ledger ORDER BY created_at DESC,id DESC LIMIT 500")
  List<Map<String, Object>> listForAdminLedger();

  @Select("SELECT COUNT(*) FROM ledger WHERE event_key=#{eventKey}")
  long countForAsset(@Param("eventKey") String eventKey);

  @Insert(
      "INSERT INTO ledger"
          + " VALUES(#{id},#{memberId},#{eventKey},#{asset},#{amount},#{balance},#{orderId},#{note},#{createdAt})")
  int insertForAsset(
      @Param("id") long id,
      @Param("memberId") long memberId,
      @Param("eventKey") String eventKey,
      @Param("asset") String asset,
      @Param("amount") long amount,
      @Param("balance") long balance,
      @Param("orderId") String orderId,
      @Param("note") String note,
      @Param("createdAt") long createdAt);

  @Select(
      "SELECT COALESCE(SUM(amount),0) FROM ledger WHERE member_id=#{memberId} AND"
          + " order_id=#{orderId} AND note='退款回收赠送积分'")
  long countForRefundSuccess(@Param("memberId") long memberId, @Param("orderId") String orderId);

  @Select(
      "SELECT COALESCE(SUM(amount),0) FROM ledger WHERE member_id=#{memberId} AND asset=#{asset}")
  long countForReconcile(@Param("memberId") long memberId, @Param("asset") String asset);
}
