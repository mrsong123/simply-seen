package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Issue;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IssueMapper extends BaseMapper<Issue> {

  @Select("SELECT COUNT(*) FROM issue WHERE state='OPEN'")
  long countForSummary();

  @Select("SELECT * FROM issue ORDER BY created_at DESC LIMIT 200")
  List<Map<String, Object>> listForReconciliation();

  @Update("UPDATE issue SET state='RESOLVED' WHERE id=#{id}")
  int updateForResolve(@Param("id") long id);

  @Select("SELECT COUNT(*) FROM issue WHERE kind=#{kind} AND resource=#{resource}")
  long countForIssue(@Param("kind") String kind, @Param("resource") String resource);

  @Insert("INSERT INTO issue VALUES(#{id},#{kind},#{resource},#{detail},#{state},#{createdAt})")
  int insertForIssue(
      @Param("id") long id,
      @Param("kind") String kind,
      @Param("resource") String resource,
      @Param("detail") String detail,
      @Param("state") String state,
      @Param("createdAt") long createdAt);

  @Select(
      "SELECT COUNT(*) FROM issue WHERE kind='POINTS_RECOVERY' AND state='OPEN' AND resource IN"
          + " (SELECT id FROM refund WHERE member_id=#{memberId})")
  long countForWithdraw(@Param("memberId") long memberId);
}
