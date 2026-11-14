package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.RuleVersion;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RuleVersionMapper extends BaseMapper<RuleVersion> {

  @Select("SELECT * FROM rule_version ORDER BY id DESC LIMIT 100")
  List<Map<String, Object>> listForAdminRules();

  @Select("SELECT COUNT(*) FROM rule_version")
  long countForRun();

  @Insert("INSERT INTO rule_version VALUES(#{id},'ACTIVE',#{config},0,0,#{createdAt})")
  int insertForRun(
      @Param("id") long id, @Param("config") String config, @Param("createdAt") long createdAt);

  @Select(
      "SELECT * FROM rule_version WHERE state='ACTIVE' ORDER BY created_at DESC,id DESC LIMIT 1")
  List<Map<String, Object>> listForRules();

  @Select("SELECT id FROM rule_version WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForMonthly(@Param("id") long id);

  @Insert("INSERT INTO rule_version VALUES(#{id},'DRAFT',#{config},#{creator},NULL,#{createdAt})")
  int insertForSaveRule(
      @Param("id") long id,
      @Param("config") String config,
      @Param("creator") long creator,
      @Param("createdAt") long createdAt);

  @Select("SELECT * FROM rule_version WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForApproveRule(@Param("id") long id);

  @Update("UPDATE rule_version SET state='ARCHIVED' WHERE state='ACTIVE'")
  int updateForApproveRule();

  @Update("UPDATE rule_version SET state='ACTIVE',reviewer=#{reviewer} WHERE id=#{id}")
  int updateForApproveRule2(@Param("reviewer") long reviewer, @Param("id") long id);
}
