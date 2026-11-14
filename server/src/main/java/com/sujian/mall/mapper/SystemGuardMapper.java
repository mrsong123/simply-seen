package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.SystemGuard;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SystemGuardMapper extends BaseMapper<SystemGuard> {

  @Select("SELECT scope_key FROM system_guard WHERE scope_key='COMMISSION' FOR UPDATE")
  Map<String, Object> findForCommission();

  @Select("SELECT scope_key FROM system_guard WHERE scope_key='RULE' FOR UPDATE")
  Map<String, Object> findForApproveRule();
}
