package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.ReconcileBatch;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReconcileBatchMapper extends BaseMapper<ReconcileBatch> {

  @Select("SELECT * FROM reconcile_batch ORDER BY created_at DESC LIMIT 100")
  List<Map<String, Object>> listForReconciliation();

  @Insert(
      "INSERT INTO reconcile_batch"
          + " VALUES(#{id},#{periodDay},#{ordersTotal},#{refundsTotal},#{transfersTotal},#{diffCount},#{createdAt})")
  int insertForReconcile(
      @Param("id") long id,
      @Param("periodDay") String periodDay,
      @Param("ordersTotal") long ordersTotal,
      @Param("refundsTotal") long refundsTotal,
      @Param("transfersTotal") long transfersTotal,
      @Param("diffCount") long diffCount,
      @Param("createdAt") long createdAt);

  @Select("SELECT * FROM reconcile_batch WHERE id=#{id}")
  Map<String, Object> findForReconcile(@Param("id") long id);
}
