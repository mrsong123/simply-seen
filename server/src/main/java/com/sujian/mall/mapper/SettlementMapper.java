package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Settlement;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SettlementMapper extends BaseMapper<Settlement> {

  @Select("SELECT * FROM settlement ORDER BY period DESC LIMIT 100")
  List<Map<String, Object>> listForSettlements();

  @Select("SELECT * FROM settlement WHERE period=#{period}")
  List<Map<String, Object>> listForRefundSuccess(@Param("period") String period);

  @Update("UPDATE settlement SET state='INVALIDATED' WHERE id=#{id}")
  int updateForRefundSuccess(@Param("id") long id);

  @Select("SELECT * FROM settlement WHERE period=#{period} FOR UPDATE")
  List<Map<String, Object>> listForMonthly(@Param("period") String period);

  @Update("UPDATE settlement SET state='DRAFT',creator=#{creator},reviewer=NULL WHERE id=#{id}")
  int updateForMonthly(@Param("creator") long creator, @Param("id") long id);

  @Insert("INSERT INTO settlement VALUES(#{id},#{period},'DRAFT',0,#{creator},NULL,#{createdAt})")
  int insertForMonthly(
      @Param("id") long id,
      @Param("period") String period,
      @Param("creator") long creator,
      @Param("createdAt") long createdAt);

  @Update("UPDATE settlement SET budget=#{budget} WHERE id=#{id}")
  int updateForMonthly2(@Param("budget") long budget, @Param("id") long id);

  @Select("SELECT * FROM settlement WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForApproveMonthly(@Param("id") long id);

  @Update("UPDATE settlement SET state='APPROVED',reviewer=#{reviewer} WHERE id=#{id}")
  int updateForApproveMonthly(@Param("reviewer") long reviewer, @Param("id") long id);
}
