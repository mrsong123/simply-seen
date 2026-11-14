package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Address;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AddressMapper extends BaseMapper<Address> {

  @Delete("DELETE FROM address WHERE id=#{id} AND member_id=#{memberId}")
  int deleteForDeleteAddress(@Param("id") long id, @Param("memberId") long memberId);

  @Select("SELECT * FROM address WHERE member_id=#{memberId} ORDER BY is_default DESC,id DESC")
  List<Map<String, Object>> listForAddresses(@Param("memberId") long memberId);

  @Update("UPDATE address SET is_default=FALSE WHERE member_id=#{memberId}")
  int updateForAddress(@Param("memberId") long memberId);

  @Insert(
      "INSERT INTO address"
          + " VALUES(#{id},#{memberId},#{name},#{phone},#{region},#{detail},#{isDefault})")
  int insertForAddress(
      @Param("id") long id,
      @Param("memberId") long memberId,
      @Param("name") String name,
      @Param("phone") String phone,
      @Param("region") String region,
      @Param("detail") String detail,
      @Param("isDefault") boolean isDefault);

  @Select("SELECT id FROM address WHERE id=#{id} AND member_id=#{memberId}")
  Map<String, Object> findForAddress(@Param("id") long id, @Param("memberId") long memberId);

  @Update(
      "UPDATE address SET"
          + " name=#{name},phone=#{phone},region=#{region},detail=#{detail},is_default=#{isDefault}"
          + " WHERE id=#{id} AND member_id=#{memberId}")
  int updateForAddress2(
      @Param("name") String name,
      @Param("phone") String phone,
      @Param("region") String region,
      @Param("detail") String detail,
      @Param("isDefault") boolean isDefault,
      @Param("id") long id,
      @Param("memberId") long memberId);

  @Select("SELECT * FROM address WHERE id=#{id} AND member_id=#{memberId}")
  Map<String, Object> findForQuote(@Param("id") long id, @Param("memberId") long memberId);
}
