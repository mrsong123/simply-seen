package com.sujian.mall.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SmsThrottleMapper {
  @Insert("INSERT INTO sms_cooldown(phone_hash,next_allowed_at) VALUES(#{phoneHash},0)"
      + " ON DUPLICATE KEY UPDATE phone_hash=phone_hash")
  void ensureCooldown(@Param("phoneHash") String phoneHash);

  @Select("SELECT next_allowed_at FROM sms_cooldown WHERE phone_hash=#{phoneHash} FOR UPDATE")
  long lockCooldown(@Param("phoneHash") String phoneHash);

  @Update("UPDATE sms_cooldown SET next_allowed_at=#{next} WHERE phone_hash=#{phoneHash}")
  void updateCooldown(@Param("phoneHash") String phoneHash, @Param("next") long next);

  @Insert("INSERT INTO sms_throttle(bucket,hits,expires_at) VALUES(#{bucket},0,#{expiresAt})"
      + " ON DUPLICATE KEY UPDATE bucket=bucket")
  void ensureBucket(@Param("bucket") String bucket, @Param("expiresAt") long expiresAt);

  @Select("SELECT hits FROM sms_throttle WHERE bucket=#{bucket} FOR UPDATE")
  int lockHits(@Param("bucket") String bucket);

  @Update("UPDATE sms_throttle SET hits=hits+1 WHERE bucket=#{bucket}")
  void increment(@Param("bucket") String bucket);

  @Delete("DELETE FROM sms_throttle WHERE expires_at<#{before}")
  void deleteExpiredBuckets(@Param("before") long before);
}
