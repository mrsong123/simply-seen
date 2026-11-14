package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Member;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MemberMapper extends BaseMapper<Member> {

  @Select(
      "SELECT id,name,created_at,promoter FROM member WHERE inviter_id=#{inviterId} ORDER BY"
          + " created_at DESC LIMIT 100")
  List<Map<String, Object>> selectDirectInvitees(@Param("inviterId") Long inviterId);

  @Select(
      "SELECT m.id,m.name,m.created_at FROM member m JOIN member p ON m.inviter_id=p.id WHERE"
          + " p.inviter_id=#{inviterId} ORDER BY m.created_at DESC LIMIT 100")
  List<Map<String, Object>> selectSecondLevelInvitees(@Param("inviterId") Long inviterId);

  @Select("SELECT COUNT(*) FROM member")
  long countMembers();

  @Select(
      "SELECT"
          + " id,name,invite_code,inviter_id,points,points_held,available,reserved,debt,promoter,blocked,created_at"
          + " FROM member ORDER BY created_at DESC LIMIT 200")
  List<Map<String, Object>> selectAdminMemberProfiles();

  @Update("UPDATE member SET blocked=#{blocked} WHERE id=#{id}")
  int setBlocked(@Param("blocked") boolean blocked, @Param("id") long id);

  @Select("SELECT id FROM member WHERE id=#{id} AND blocked=FALSE")
  Map<String, Object> selectActiveMemberId(@Param("id") long id);

  @Select("SELECT COUNT(*) FROM member WHERE openid=#{openid}")
  long countByOpenid(@Param("openid") String openid);

  @Select("SELECT * FROM member WHERE openid=#{openid}")
  Map<String, Object> selectByOpenid(@Param("openid") String openid);

  @Select(
      "SELECT * FROM member WHERE invite_code=#{inviteCode} AND promoter=TRUE AND blocked=FALSE")
  Map<String, Object> selectEligibleInviterByCode(@Param("inviteCode") String inviteCode);

  @Insert(
      "INSERT INTO member(id,openid,name,invite_code,inviter_id,created_at)"
          + " VALUES(#{id},#{openid,jdbcType=VARCHAR},#{name},#{inviteCode},#{inviterId},#{createdAt})")
  int insertRegisteredMember(
      @Param("id") long id,
      @Param("openid") String openid,
      @Param("name") String name,
      @Param("inviteCode") String inviteCode,
      @Param("inviterId") Long inviterId,
      @Param("createdAt") long createdAt);

  @Update("UPDATE member SET openid=#{openid} WHERE id=#{id}"
      + " AND (openid IS NULL OR openid LIKE 'dev:%')")
  int bindWechatOpenid(@Param("id") long id, @Param("openid") String openid);

  @Select(
      "SELECT"
          + " id,name,invite_code,inviter_id,points,points_held,available,reserved,debt,promoter,created_at"
          + " FROM member WHERE id=#{id}")
  Map<String, Object> selectPublicProfile(@Param("id") long id);

  @Update("UPDATE member SET name=#{name} WHERE id=#{id}")
  int updateDisplayName(@Param("name") String name, @Param("id") long id);

  @Update("UPDATE member SET promoter=TRUE WHERE id=#{id} AND blocked=FALSE")
  int acceptPromoterAgreement(@Param("id") long id);

  @Select("SELECT id FROM member WHERE id=#{id} FOR UPDATE")
  Map<String, Object> lockMemberId(@Param("id") long id);

  @Select("SELECT * FROM member WHERE id=#{id}")
  Map<String, Object> selectAccountById(@Param("id") long id);

  @Select("SELECT * FROM member WHERE id=#{id} FOR UPDATE")
  Map<String, Object> lockAccountById(@Param("id") long id);

  @Select("SELECT id,openid FROM member WHERE id=#{id}")
  Map<String, Object> selectOpenidByMemberId(@Param("id") long id);

  @Select("SELECT * FROM member ORDER BY id")
  List<Map<String, Object>> selectAccountsForReconciliation();

  @UpdateProvider(type = MemberSqlProvider.class, method = "updateAsset")
  int updateAssetBalance(
      @Param("asset") String asset,
      @Param("balance") long balance,
      @Param("memberId") long memberId);
}
