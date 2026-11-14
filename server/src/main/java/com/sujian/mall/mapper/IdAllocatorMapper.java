package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.IdAllocator;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdAllocatorMapper extends BaseMapper<IdAllocator> {

  /** Id allocation persistence operation. */
  @Select("SELECT next_id FROM id_allocator WHERE scope_key='GLOBAL' FOR UPDATE")
  long lockNextId();

  /** Id allocation persistence operation. */
  @Update("UPDATE id_allocator SET next_id=#{nextId} WHERE scope_key='GLOBAL'")
  int advanceNextId(@Param("nextId") long nextId);
}
