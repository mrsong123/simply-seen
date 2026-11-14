package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.SessionToken;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SessionTokenMapper extends BaseMapper<SessionToken> {

  @Delete("DELETE FROM session_token WHERE actor_id=#{actorId} AND kind='MEMBER'")
  int deleteForBlock(@Param("actorId") long actorId);

  @Delete("DELETE FROM session_token WHERE actor_id=#{actorId} AND kind='ADMIN'")
  int deleteForAccountActive(@Param("actorId") long actorId);

  @Select("SELECT * FROM session_token WHERE token_hash=#{tokenHash} AND expires_at>#{expiresAt}")
  List<Map<String, Object>> listForActor(
      @Param("tokenHash") String tokenHash, @Param("expiresAt") long expiresAt);

  @Insert("INSERT INTO session_token VALUES(#{tokenHash},#{actorId},#{kind},#{expiresAt})")
  int insertForSession(
      @Param("tokenHash") String tokenHash,
      @Param("actorId") long actorId,
      @Param("kind") String kind,
      @Param("expiresAt") long expiresAt);

  @Delete("DELETE FROM session_token WHERE token_hash=#{tokenHash}")
  int deleteForLogout(@Param("tokenHash") String tokenHash);

  @Delete("DELETE FROM session_token WHERE expires_at<#{expiresAt}")
  int deleteForMaintain(@Param("expiresAt") long expiresAt);
}
