package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Outbox;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OutboxMapper extends BaseMapper<Outbox> {

  @Select("SELECT * FROM outbox WHERE state<>'DONE' ORDER BY created_at LIMIT 100")
  List<Map<String, Object>> listForReconciliation();

  @Select("SELECT COUNT(*) FROM outbox WHERE id=#{id}")
  long countForEvent(@Param("id") String id);

  @Insert(
      "INSERT INTO outbox(id,kind,aggregate_id,state,next_at,created_at)"
          + " VALUES(#{id},#{kind},#{aggregateId},'READY',#{nextAt},#{createdAt})")
  int insertForEvent(
      @Param("id") String id,
      @Param("kind") String kind,
      @Param("aggregateId") String aggregateId,
      @Param("nextAt") long nextAt,
      @Param("createdAt") long createdAt);

  @Update("UPDATE outbox SET state='DONE' WHERE id=#{id}")
  int updateForRun(@Param("id") String id);

  @Update("UPDATE outbox SET attempts=0 WHERE id=#{id}")
  int updateForRun2(@Param("id") String id);

  @Update(
      "UPDATE outbox SET state='READY',next_at=#{nextAt},last_error=#{lastError} WHERE id=#{id}")
  int updateForRun3(
      @Param("nextAt") long nextAt, @Param("lastError") String lastError, @Param("id") String id);

  @Select(
      "SELECT * FROM outbox WHERE (state='READY' OR state='SENDING') AND next_at<=#{nextAt} ORDER"
          + " BY created_at LIMIT 1 FOR UPDATE")
  List<Map<String, Object>> listForClaim(@Param("nextAt") long nextAt);

  @Update("UPDATE outbox SET state='SENDING',attempts=attempts+1,next_at=#{nextAt} WHERE id=#{id}")
  int updateForClaim(@Param("nextAt") long nextAt, @Param("id") String id);
}
