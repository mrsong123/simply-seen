package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Withdrawal;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WithdrawalMapper extends BaseMapper<Withdrawal> {

  @Select("SELECT * FROM withdrawal WHERE member_id=#{memberId} ORDER BY created_at DESC LIMIT 100")
  List<Map<String, Object>> listForWithdrawals(@Param("memberId") long memberId);

  @Select("SELECT COUNT(*) FROM withdrawal WHERE state='REVIEWING'")
  long countForSummary();

  @Select("SELECT * FROM withdrawal ORDER BY created_at DESC LIMIT 200")
  List<Map<String, Object>> listForAdminWithdrawals();

  @Insert(
      "INSERT INTO withdrawal(id,member_id,gross,tax,net,state,created_at)"
          + " VALUES(#{id},#{memberId},#{gross},0,#{net},'REVIEWING',#{createdAt})")
  int insertForWithdraw(
      @Param("id") String id,
      @Param("memberId") long memberId,
      @Param("gross") long gross,
      @Param("net") long net,
      @Param("createdAt") long createdAt);

  @Select("SELECT * FROM withdrawal WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForReviewWithdrawal(@Param("id") String id);

  @Update("UPDATE withdrawal SET reviewer=#{reviewer},reason=#{reason} WHERE id=#{id}")
  int updateForReviewWithdrawal(
      @Param("reviewer") long reviewer, @Param("reason") String reason, @Param("id") String id);

  @Update(
      "UPDATE withdrawal SET"
          + " state='APPROVED',reviewer=#{reviewer},tax=#{tax},net=gross-#{net},reason=#{reason}"
          + " WHERE id=#{id}")
  int updateForReviewWithdrawal2(
      @Param("reviewer") long reviewer,
      @Param("tax") long tax,
      @Param("net") long net,
      @Param("reason") String reason,
      @Param("id") String id);

  @Update("UPDATE withdrawal SET state='SUBMITTED',executor=#{executor} WHERE id=#{id}")
  int updateForExecuteWithdrawal(@Param("executor") long executor, @Param("id") String id);

  @Update("UPDATE withdrawal SET state=#{state} WHERE id=#{id}")
  int updateForReleaseWithdrawal(@Param("state") String state, @Param("id") String id);

  @Update("UPDATE withdrawal SET state='SUCCESS',channel_id=#{channelId} WHERE id=#{id}")
  int updateForTransferResult(@Param("channelId") String channelId, @Param("id") String id);

  @Update(
      "UPDATE withdrawal SET state=#{state},package_info=#{packageInfo},channel_id=#{channelId}"
          + " WHERE id=#{id}")
  int updateForTransferResult2(
      @Param("state") String state,
      @Param("packageInfo") String packageInfo,
      @Param("channelId") String channelId,
      @Param("id") String id);

  @Select("SELECT * FROM withdrawal WHERE id=#{id}")
  Map<String, Object> findForTransfer(@Param("id") String id);

  @Select("SELECT state FROM withdrawal WHERE id=#{id}")
  Map<String, Object> findForTransfer2(@Param("id") String id);

  @Select("SELECT * FROM withdrawal WHERE state='SUCCESS' ORDER BY created_at")
  List<Map<String, Object>> listForReconcile();

  @Select("SELECT COALESCE(SUM(net),0) FROM withdrawal WHERE state='SUCCESS'")
  long countForReconcile();
}
