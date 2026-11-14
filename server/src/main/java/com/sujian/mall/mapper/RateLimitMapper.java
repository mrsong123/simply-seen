package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.RateLimit;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RateLimitMapper extends BaseMapper<RateLimit> {

  @Select("SELECT * FROM rate_limit WHERE bucket=#{bucket} FOR UPDATE")
  List<Map<String, Object>> listForLimitInside(@Param("bucket") String bucket);

  @Insert("INSERT INTO rate_limit VALUES(#{bucket},1,#{windowAt})")
  int insertForLimitInside(@Param("bucket") String bucket, @Param("windowAt") long windowAt);

  @Update("UPDATE rate_limit SET hits=1,window_at=#{windowAt} WHERE bucket=#{bucket}")
  int updateForLimitInside(@Param("windowAt") long windowAt, @Param("bucket") String bucket);

  @Update("UPDATE rate_limit SET hits=hits+1 WHERE bucket=#{bucket}")
  int updateForLimitInside2(@Param("bucket") String bucket);

  @Delete("DELETE FROM rate_limit WHERE window_at<#{windowAt}")
  int deleteForMaintain(@Param("windowAt") long windowAt);
}
