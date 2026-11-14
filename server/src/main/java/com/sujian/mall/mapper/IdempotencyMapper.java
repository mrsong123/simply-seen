package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Idempotency;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdempotencyMapper extends BaseMapper<Idempotency> {

  @Select("SELECT * FROM idempotency WHERE scope_key=#{scopeKey}")
  List<Map<String, Object>> listForIdem(@Param("scopeKey") String scopeKey);

  @Insert("INSERT INTO idempotency VALUES(#{scopeKey},#{payloadHash},#{response},#{createdAt})")
  int insertForIdem(
      @Param("scopeKey") String scopeKey,
      @Param("payloadHash") String payloadHash,
      @Param("response") String response,
      @Param("createdAt") long createdAt);
}
