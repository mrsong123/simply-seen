package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Audit;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditMapper extends BaseMapper<Audit> {

  @Select("SELECT * FROM audit ORDER BY created_at DESC,id DESC LIMIT 500")
  List<Map<String, Object>> listForAudit();

  @Insert("INSERT INTO audit VALUES(#{id},#{actorId},#{action},#{resource},#{detail},#{createdAt})")
  int insertForAudit(
      @Param("id") long id,
      @Param("actorId") long actorId,
      @Param("action") String action,
      @Param("resource") String resource,
      @Param("detail") String detail,
      @Param("createdAt") long createdAt);
}
