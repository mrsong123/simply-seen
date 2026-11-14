package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.AdminAccount;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminAccountMapper extends BaseMapper<AdminAccount> {

  @Select("SELECT id,username,role FROM admin_account WHERE id=#{id}")
  Map<String, Object> findForAdminMe(@Param("id") long id);

  @Select("SELECT id,username,role,active FROM admin_account ORDER BY id")
  List<Map<String, Object>> listForAccounts();

  @Select("SELECT * FROM admin_account WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForAccountActive(@Param("id") long id);

  @Select("SELECT COUNT(*) FROM admin_account WHERE role='ADMIN' AND active=TRUE")
  long countForAccountActive();

  @Update("UPDATE admin_account SET active=#{active} WHERE id=#{id}")
  int updateForAccountActive(@Param("active") boolean active, @Param("id") long id);

  @Select("SELECT id FROM admin_account WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForResetPassword(@Param("id") long id);

  @Update("UPDATE admin_account SET password_hash=#{passwordHash} WHERE id=#{id}")
  int updateForResetPassword(@Param("passwordHash") String passwordHash, @Param("id") long id);

  @Select("SELECT * FROM admin_account WHERE id=#{id} AND active=TRUE")
  Map<String, Object> findForActor(@Param("id") long id);

  @Select("SELECT * FROM admin_account WHERE username=#{username} AND active=TRUE")
  List<Map<String, Object>> listForAdminLogin(@Param("username") String username);

  @Insert("INSERT INTO admin_account VALUES(#{id},#{username},#{passwordHash},#{role},TRUE)")
  int insertForCreateAdmin(
      @Param("id") long id,
      @Param("username") String username,
      @Param("passwordHash") String passwordHash,
      @Param("role") String role);

  @Select("SELECT COUNT(*) FROM admin_account")
  long countForRun();
}
