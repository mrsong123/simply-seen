package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.SmsChallenge;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SmsChallengeMapper extends BaseMapper<SmsChallenge> {
  @Select("SELECT * FROM sms_challenge WHERE id=#{id} FOR UPDATE")
  SmsChallenge lockById(@Param("id") String id);

  @Update("UPDATE sms_challenge SET state='SUPERSEDED',code_hash='' WHERE phone_hash=#{phoneHash}"
      + " AND purpose=#{purpose} AND member_id=#{memberId} AND state IN ('ISSUING','SENT')")
  void supersede(@Param("phoneHash") String phoneHash, @Param("purpose") String purpose,
      @Param("memberId") long memberId);

  @Update("UPDATE sms_challenge SET state='SENT',provider_request_id=#{requestId},development=#{development}"
      + " WHERE id=#{id} AND state='ISSUING'")
  int sent(@Param("id") String id, @Param("requestId") String requestId,
      @Param("development") boolean development);

  @Update("UPDATE sms_challenge SET state='FAILED',code_hash='' WHERE id=#{id} AND state='ISSUING'")
  void failed(@Param("id") String id);

  @Update("UPDATE sms_challenge SET attempts=attempts+1,state=#{state},code_hash=#{codeHash} WHERE id=#{id}")
  void recordAttempt(@Param("id") String id, @Param("state") String state,
      @Param("codeHash") String codeHash);

  @Delete("DELETE FROM sms_challenge WHERE created_at<#{before}")
  void deleteExpiredHistory(@Param("before") long before);
}
