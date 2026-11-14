package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.MemberIdentity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MemberIdentityMapper extends BaseMapper<MemberIdentity> {
  @Select("SELECT * FROM member_identity WHERE member_id=#{memberId} FOR UPDATE")
  MemberIdentity lockByMemberId(@Param("memberId") long memberId);

  @Update("UPDATE member_identity SET state=#{row.state},verified_id_hash=#{row.verifiedIdHash},"
      + "provider_request_id=#{row.providerRequestId},development=#{row.development},"
      + "verified_at=#{row.verifiedAt},rejection_reason=#{row.rejectionReason},updated_at=#{row.updatedAt}"
      + " WHERE member_id=#{row.memberId} AND active_attempt_id=#{attemptId}")
  int completeAttempt(@Param("row") MemberIdentity row, @Param("attemptId") String attemptId);
}
