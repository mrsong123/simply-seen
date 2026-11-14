package com.sujian.mall.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountSecurityLockMapper {
  @Insert("INSERT INTO account_security_lock(lock_key) VALUES(#{key})"
      + " ON DUPLICATE KEY UPDATE lock_key=lock_key")
  void ensure(@Param("key") String key);

  @Select("SELECT lock_key FROM account_security_lock WHERE lock_key=#{key} FOR UPDATE")
  String lock(@Param("key") String key);
}
