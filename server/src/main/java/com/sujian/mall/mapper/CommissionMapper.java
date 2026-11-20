package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Commission;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommissionMapper extends BaseMapper<Commission> {

  @Select(
      "SELECT id,order_id,level_no,amount,state,created_at,settled_at FROM commission WHERE"
          + " beneficiary_id=#{beneficiaryId} ORDER BY created_at DESC LIMIT 200")
  List<Map<String, Object>> listForTeam(@Param("beneficiaryId") long beneficiaryId);

  @Select("SELECT * FROM commission ORDER BY created_at DESC LIMIT 200")
  List<Map<String, Object>> listForCommissions();

  @Select("SELECT COUNT(*) FROM commission WHERE order_id=#{orderId}")
  long countForCommission(@Param("orderId") String orderId);

  @Select(
      "SELECT c.original_amount FROM commission c"
          + " JOIN shop_order o ON o.id=c.order_id"
          + " WHERE o.paid_at>=#{paidMonthStart} AND o.paid_at<#{nextPaidMonthStart} FOR UPDATE")
  List<Long> listOriginalAmountsForPaidMonthBudget(
      @Param("paidMonthStart") long paidMonthStart,
      @Param("nextPaidMonthStart") long nextPaidMonthStart);

  @Insert(
      "INSERT INTO"
          + " commission(id,order_id,beneficiary_id,buyer_id,level_no,amount,original_amount,state,rule_snapshot,created_at)"
          + " VALUES(#{id},#{orderId},#{beneficiaryId},#{buyerId},#{levelNo},#{amount},#{originalAmount},'PENDING',#{ruleSnapshot},#{createdAt})")
  int insertForCommission(
      @Param("id") long id,
      @Param("orderId") String orderId,
      @Param("beneficiaryId") long beneficiaryId,
      @Param("buyerId") long buyerId,
      @Param("levelNo") long levelNo,
      @Param("amount") long amount,
      @Param("originalAmount") long originalAmount,
      @Param("ruleSnapshot") String ruleSnapshot,
      @Param("createdAt") long createdAt);

  @Select("SELECT * FROM commission WHERE order_id=#{orderId} ORDER BY beneficiary_id FOR UPDATE")
  List<Map<String, Object>> listForRefundSuccess(@Param("orderId") String orderId);

  @Update("UPDATE commission SET amount=#{amount},state=#{state} WHERE id=#{id}")
  int updateForRefundSuccess(
      @Param("amount") long amount, @Param("state") String state, @Param("id") long id);

  @Select(
      "SELECT c.id FROM commission c JOIN shop_order o ON o.id=c.order_id WHERE c.state='PENDING'"
          + " AND o.state='COMPLETED' AND o.completed_at IS NOT NULL ORDER BY c.id LIMIT 100")
  List<Map<String, Object>> listForSettleCommissions();

  @Select("SELECT * FROM commission WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForSettleCommissions(@Param("id") long id);

  @Update("UPDATE commission SET state='SETTLED',settled_at=#{settledAt} WHERE id=#{id}")
  int updateForSettleCommissions(@Param("settledAt") long settledAt, @Param("id") long id);
}
