package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.SettlementAward;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SettlementAwardMapper extends BaseMapper<SettlementAward> {

  @Select(
      "SELECT a.*,s.period,s.state FROM settlement_award a JOIN settlement s ON"
          + " s.id=a.settlement_id WHERE a.member_id=#{memberId} AND s.state='APPROVED' ORDER BY"
          + " s.period DESC")
  List<Map<String, Object>> listForAwards(@Param("memberId") long memberId);

  @Select("SELECT * FROM settlement_award WHERE settlement_id=#{settlementId} ORDER BY rank_no")
  List<Map<String, Object>> listForSettlements(@Param("settlementId") long settlementId);

  @Delete("DELETE FROM settlement_award WHERE settlement_id=#{settlementId}")
  int deleteForMonthly(@Param("settlementId") long settlementId);

  @Insert(
      "INSERT INTO settlement_award"
          + " VALUES(#{id},#{settlementId},#{memberId},#{sales},#{amount},#{rankNo})")
  int insertForMonthly(
      @Param("id") long id,
      @Param("settlementId") long settlementId,
      @Param("memberId") long memberId,
      @Param("sales") long sales,
      @Param("amount") long amount,
      @Param("rankNo") long rankNo);

  @Select("SELECT * FROM settlement_award WHERE settlement_id=#{settlementId} ORDER BY member_id")
  List<Map<String, Object>> listForApproveMonthly(@Param("settlementId") long settlementId);
}
