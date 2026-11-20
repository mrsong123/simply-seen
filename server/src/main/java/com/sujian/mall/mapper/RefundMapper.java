package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Refund;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

  @Select("SELECT COUNT(*) FROM refund WHERE state='REQUESTED'")
  long countForSummary();

  @Select("SELECT * FROM refund ORDER BY created_at DESC LIMIT 200")
  List<Map<String, Object>> listForRefunds();

  @Select("SELECT * FROM refund WHERE order_id=#{orderId} ORDER BY created_at DESC")
  List<Map<String, Object>> listForOrder(@Param("orderId") String orderId);

  @Select(
      "SELECT COUNT(*) FROM refund WHERE order_id=#{orderId} AND state IN"
          + " ('REQUESTED','REVIEWED','PROCESSING','ABNORMAL')")
  long countForShip(@Param("orderId") String orderId);

  @Insert(
      "INSERT INTO"
          + " refund(id,order_id,member_id,state,amount,points,line_snapshot,reason,creator,created_at)"
          + " VALUES(#{id},#{orderId},#{memberId},'REQUESTED',#{amount},#{points},#{lineSnapshot},#{reason},#{creator},#{createdAt})")
  int insertForRefund(
      @Param("id") String id,
      @Param("orderId") String orderId,
      @Param("memberId") long memberId,
      @Param("amount") long amount,
      @Param("points") long points,
      @Param("lineSnapshot") String lineSnapshot,
      @Param("reason") String reason,
      @Param("creator") long creator,
      @Param("createdAt") long createdAt);

  @Select("SELECT * FROM refund WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForReviewRefund(@Param("id") String id);

  @Update("UPDATE refund SET state='CLOSED',reviewer=#{reviewer},reason=#{reason} WHERE id=#{id}")
  int updateForReviewRefund(
      @Param("reviewer") long reviewer, @Param("reason") String reason, @Param("id") String id);

  @Update("UPDATE refund SET state='REVIEWED',reviewer=#{reviewer} WHERE id=#{id}")
  int updateForReviewRefund2(@Param("reviewer") long reviewer, @Param("id") String id);

  @Update("UPDATE refund SET state='SUCCESS',channel_id=#{channelId} WHERE id=#{id}")
  int updateForRefundSuccess(@Param("channelId") String channelId, @Param("id") String id);

  @Select("SELECT * FROM refund WHERE id=#{id}")
  Map<String, Object> findForRefund(@Param("id") String id);

  @Update("UPDATE refund SET state='PROCESSING' WHERE id=#{id} AND state='REVIEWED'")
  int updateForRefund(@Param("id") String id);

  @Select("SELECT state FROM refund WHERE id=#{id}")
  Map<String, Object> findForRefund2(@Param("id") String id);

  @Update("UPDATE refund SET state='ABNORMAL' WHERE id=#{id} AND state<>'SUCCESS'")
  int updateForRefundResult(@Param("id") String id);

  @Update("UPDATE refund SET state='CLOSED',channel_id=#{channelId} WHERE id=#{id} AND state<>'SUCCESS'")
  int closeByChannel(@Param("id") String id, @Param("channelId") String channelId);

  @Select("SELECT * FROM refund WHERE state='SUCCESS' AND amount>0 ORDER BY created_at")
  List<Map<String, Object>> listForReconcile();

  @Select("SELECT COALESCE(SUM(amount),0) FROM refund WHERE state='SUCCESS'")
  long countForReconcile();
}
