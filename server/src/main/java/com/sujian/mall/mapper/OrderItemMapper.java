package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.OrderItem;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

  @Insert(
      "INSERT INTO"
          + " order_item(id,order_id,sku_id,product_id,name,image,quantity,unit_price,cost,cash_amount,points_amount,commission_percent)"
          + " VALUES(#{id},#{orderId},#{skuId},#{productId},#{name},#{image},#{quantity},#{unitPrice},#{cost},#{cashAmount},#{pointsAmount},#{commissionPercent})")
  int insertForCreate(
      @Param("id") long id,
      @Param("orderId") String orderId,
      @Param("skuId") long skuId,
      @Param("productId") long productId,
      @Param("name") String name,
      @Param("image") String image,
      @Param("quantity") long quantity,
      @Param("unitPrice") long unitPrice,
      @Param("cost") long cost,
      @Param("cashAmount") long cashAmount,
      @Param("pointsAmount") long pointsAmount,
      @Param("commissionPercent") long commissionPercent);

  @Select("SELECT * FROM order_item WHERE order_id=#{orderId} ORDER BY id")
  List<Map<String, Object>> listForOrders(@Param("orderId") String orderId);

  @Select("SELECT * FROM order_item WHERE order_id=#{orderId} ORDER BY sku_id")
  List<Map<String, Object>> listForPaid(@Param("orderId") String orderId);

  @Select("SELECT * FROM order_item WHERE order_id=#{orderId}")
  List<Map<String, Object>> listForCommission(@Param("orderId") String orderId);

  @Select("SELECT * FROM order_item WHERE id=#{id} AND order_id=#{orderId}")
  Map<String, Object> findForRefund(@Param("id") long id, @Param("orderId") String orderId);

  @Select("SELECT SUM(quantity-refunded_quantity) FROM order_item WHERE order_id=#{orderId}")
  long countForRefund(@Param("orderId") String orderId);

  @Update(
      "UPDATE order_item SET"
          + " refunded_quantity=refunded_quantity+#{refundedQuantity},refunded_cash=refunded_cash+#{refundedCash},refunded_points=refunded_points+#{refundedPoints}"
          + " WHERE id=#{id}")
  int updateForRefundSuccess(
      @Param("refundedQuantity") long refundedQuantity,
      @Param("refundedCash") long refundedCash,
      @Param("refundedPoints") long refundedPoints,
      @Param("id") long id);
}
