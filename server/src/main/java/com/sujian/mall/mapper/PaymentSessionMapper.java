package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.PaymentSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaymentSessionMapper extends BaseMapper<PaymentSession> {
  @Update("UPDATE payment_session SET channel=#{channel},attempt_id=#{attemptId},state='CREATING',"
      + "response_payload=NULL,lease_until=#{leaseUntil},expires_at=#{expiresAt},updated_at=#{now}"
      + " WHERE order_id=#{orderId}")
  int reserve(@Param("orderId") String orderId, @Param("channel") String channel,
      @Param("attemptId") String attemptId, @Param("leaseUntil") long leaseUntil,
      @Param("expiresAt") long expiresAt, @Param("now") long now);

  @Update("UPDATE payment_session SET state='READY',response_payload=#{response},lease_until=0,"
      + "updated_at=#{now} WHERE order_id=#{orderId} AND attempt_id=#{attemptId} AND state='CREATING'")
  int ready(@Param("orderId") String orderId, @Param("attemptId") String attemptId,
      @Param("response") String response, @Param("now") long now);

  @Update("UPDATE payment_session SET state=#{state},lease_until=0,updated_at=#{now}"
      + " WHERE order_id=#{orderId} AND attempt_id=#{attemptId} AND state='CREATING'")
  int failed(@Param("orderId") String orderId, @Param("attemptId") String attemptId,
      @Param("state") String state, @Param("now") long now);
}
