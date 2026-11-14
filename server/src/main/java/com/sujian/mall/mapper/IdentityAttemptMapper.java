package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.IdentityAttempt;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdentityAttemptMapper extends BaseMapper<IdentityAttempt> {
  @Update("UPDATE identity_attempt SET state=#{state},provider_request_id=#{requestId},"
      + "development=#{development},reason=#{reason},finished_at=#{finishedAt}"
      + " WHERE id=#{id} AND state IN ('PENDING','UNKNOWN')")
  int finish(@Param("id") String id, @Param("state") String state,
      @Param("requestId") String requestId, @Param("development") boolean development,
      @Param("reason") String reason, @Param("finishedAt") long finishedAt);
}
