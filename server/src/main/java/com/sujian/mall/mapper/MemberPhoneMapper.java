package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.MemberPhone;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MemberPhoneMapper extends BaseMapper<MemberPhone> {
  @Select("SELECT * FROM member_phone WHERE phone_hash=#{hash}")
  MemberPhone findByPhoneHash(@Param("hash") String hash);
}
