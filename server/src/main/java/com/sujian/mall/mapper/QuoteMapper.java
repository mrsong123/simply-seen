package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Quote;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface QuoteMapper extends BaseMapper<Quote> {

  @Insert("INSERT INTO quote VALUES(#{id},#{memberId},#{addressId},#{payload},#{expiresAt})")
  int insertForQuote(
      @Param("id") String id,
      @Param("memberId") long memberId,
      @Param("addressId") long addressId,
      @Param("payload") String payload,
      @Param("expiresAt") long expiresAt);

  @Select("SELECT * FROM quote WHERE id=#{id} AND member_id=#{memberId}")
  Map<String, Object> findForCreate(@Param("id") String id, @Param("memberId") long memberId);

  @Delete("DELETE FROM quote WHERE id=#{id}")
  int deleteForCreate(@Param("id") String id);

  @Delete("DELETE FROM quote WHERE expires_at<#{expiresAt}")
  int deleteForMaintain(@Param("expiresAt") long expiresAt);
}
