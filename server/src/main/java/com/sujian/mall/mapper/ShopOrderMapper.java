package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.ShopOrder;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {

  @Select("SELECT COUNT(*) FROM shop_order")
  long countOrders();

  @Select(
      "SELECT COALESCE(SUM(cash_due-cash_refunded),0) FROM shop_order WHERE"
          + " payment_state='SUCCESS'")
  long sumNetPaidCash();

  @Select("SELECT COUNT(*) FROM shop_order WHERE state='PAID'")
  long countAwaitingShipment();

  @Select("SELECT created_at,cash_due,state FROM shop_order ORDER BY created_at DESC LIMIT 200")
  List<Map<String, Object>> selectRecentOrderTrend();

  @Select(
      "SELECT o.id,o.member_id,o.state,o.payment_state,o.item_amount,o.freight,o.points_used,"
          + "o.cash_due,o.cash_refunded,o.points_refunded,o.address_snapshot,o.rule_snapshot,o.remark,"
          + "o.transaction_id,o.expires_at,o.created_at,o.paid_at,o.completed_at,o.carrier,"
          + "o.tracking_no,o.payment_channel,m.name AS member_name FROM shop_order o JOIN member m ON m.id=o.member_id ORDER"
          + " BY o.created_at DESC LIMIT 200")
  List<Map<String, Object>> selectAdminOrders();

  @Select("SELECT member_id FROM shop_order WHERE id=#{id}")
  Map<String, Object> selectOrderOwner(@Param("id") String id);

  @Insert(
      "INSERT INTO"
          + " shop_order(id,member_id,state,payment_state,item_amount,freight,points_used,cash_due,address_snapshot,rule_snapshot,remark,expires_at,created_at)"
          + " VALUES(#{id},#{memberId},'CREATED','INIT',#{itemAmount},#{freight},#{pointsUsed},#{cashDue},#{addressSnapshot},#{ruleSnapshot},#{remark},#{expiresAt},#{createdAt})")
  int insertPendingOrder(
      @Param("id") String id,
      @Param("memberId") long memberId,
      @Param("itemAmount") long itemAmount,
      @Param("freight") long freight,
      @Param("pointsUsed") long pointsUsed,
      @Param("cashDue") long cashDue,
      @Param("addressSnapshot") String addressSnapshot,
      @Param("ruleSnapshot") String ruleSnapshot,
      @Param("remark") String remark,
      @Param("expiresAt") long expiresAt,
      @Param("createdAt") long createdAt);

  @Select("SELECT * FROM shop_order WHERE member_id=#{memberId} ORDER BY created_at DESC LIMIT 100")
  List<Map<String, Object>> selectMemberOrders(@Param("memberId") long memberId);

  @Select("SELECT * FROM shop_order WHERE id=#{id} AND member_id=#{memberId}")
  Map<String, Object> selectOwnedOrder(@Param("id") String id, @Param("memberId") long memberId);

  @Select("SELECT * FROM shop_order WHERE id=#{id} FOR UPDATE")
  Map<String, Object> lockOrderByNo(@Param("id") String id);

  @Update(
      "UPDATE shop_order SET"
          + " state='PAID',payment_state='SUCCESS',transaction_id=#{transactionId},paid_at=#{paidAt}"
          + " WHERE id=#{id}")
  int confirmPayment(
      @Param("transactionId") String transactionId,
      @Param("paidAt") long paidAt,
      @Param("id") String id);

  @Update("UPDATE shop_order SET state='CLOSED',payment_state='CLOSED' WHERE id=#{id}")
  int closeUnpaidOrder(@Param("id") String id);

  @Select("SELECT * FROM shop_order WHERE id=#{id} AND member_id=#{memberId} FOR UPDATE")
  Map<String, Object> lockOwnedOrderByNo(@Param("id") String id, @Param("memberId") long memberId);

  @Update("UPDATE shop_order SET payment_state='PREPAY' WHERE id=#{id} AND state='CREATED'")
  int markPrepay(@Param("id") String id);

  @Update("UPDATE shop_order SET payment_state='PREPAY',payment_channel=#{channel},payment_app_id=#{appId}"
      + " WHERE id=#{id} AND state='CREATED' AND payment_state<>'CLOSING'")
  int markPrepayChannel(@Param("id") String id, @Param("channel") String channel,
      @Param("appId") String appId);

  @Update("UPDATE shop_order SET payment_state='CLOSING' WHERE id=#{id} AND state='CREATED'")
  int markPaymentClosing(@Param("id") String id);

  @Update("UPDATE shop_order SET payment_payer_openid=#{openid} WHERE id=#{id} AND payment_state='SUCCESS'")
  int recordPaymentPayer(@Param("id") String id, @Param("openid") String openid);

  @Update(
      "UPDATE shop_order SET payment_state='PREPAY' WHERE id=#{id} AND state='CREATED' AND"
          + " payment_state IN ('INIT','PREPAY')")
  int markPrepayIfUnconfirmed(@Param("id") String id);

  @Select("SELECT * FROM shop_order WHERE id=#{id}")
  Map<String, Object> selectOrderByNo(@Param("id") String id);

  @Update(
      "UPDATE shop_order SET state='SHIPPED',carrier=#{carrier},tracking_no=#{trackingNo} WHERE"
          + " id=#{id}")
  int markShipped(
      @Param("carrier") String carrier,
      @Param("trackingNo") String trackingNo,
      @Param("id") String id);

  @Update("UPDATE shop_order SET state='COMPLETED',completed_at=#{completedAt} WHERE id=#{id}")
  int markCompleted(@Param("completedAt") long completedAt, @Param("id") String id);

  @Update(
      "UPDATE shop_order SET"
          + " cash_refunded=#{cashRefunded},points_refunded=#{pointsRefunded},state=#{state} WHERE"
          + " id=#{id}")
  int recordRefundTotals(
      @Param("cashRefunded") long cashRefunded,
      @Param("pointsRefunded") long pointsRefunded,
      @Param("state") String state,
      @Param("id") String id);

  @Select(
      "SELECT member_id,SUM(cash_due-freight-cash_refunded) AS sales,MIN(paid_at) AS first_paid"
          + " FROM shop_order WHERE paid_at>=#{paidAt} AND paid_at<#{paidAt2} AND"
          + " payment_state='SUCCESS' AND state<>'REFUNDED' AND cash_due-freight-cash_refunded>0"
          + " GROUP BY member_id ORDER BY sales DESC,first_paid,member_id")
  List<Map<String, Object>> selectMonthlyNetSales(
      @Param("paidAt") long paidAt, @Param("paidAt2") long paidAt2);

  @Select(
      "SELECT * FROM shop_order WHERE state='CREATED' AND expires_at<#{expiresAt} ORDER BY"
          + " created_at LIMIT 50")
  List<Map<String, Object>> selectExpiredUnpaidOrders(@Param("expiresAt") long expiresAt);

  @Select("SELECT * FROM shop_order WHERE payment_state='SUCCESS' AND cash_due>0 ORDER BY paid_at")
  List<Map<String, Object>> selectPaidOrdersForReconciliation();

  @Select("SELECT COALESCE(SUM(cash_due),0) FROM shop_order WHERE payment_state='SUCCESS'")
  long sumConfirmedCash();
}
