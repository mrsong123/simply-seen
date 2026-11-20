package com.sujian.mall.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
/** cart_item has a composite (member_id, sku_id) key; every write checks both columns. */
public interface CartItemMapper {

  @Select(
      "SELECT c.sku_id,c.quantity,s.name AS sku_name,s.price,CASE WHEN s.active=TRUE THEN s.stock-s.held ELSE 0 END AS stock,p.id AS"
          + " product_id,p.name,p.image,p.color,CASE WHEN s.active=TRUE THEN p.status ELSE 'OFF_SALE' END AS status FROM cart_item c JOIN sku s ON"
          + " c.sku_id=s.id JOIN product p ON s.product_id=p.id WHERE c.member_id=#{memberId} ORDER"
          + " BY p.id")
  List<Map<String, Object>> listForCart(@Param("memberId") long memberId);

  @Delete("DELETE FROM cart_item WHERE member_id=#{memberId} AND sku_id=#{skuId}")
  int deleteForCart(@Param("memberId") long memberId, @Param("skuId") long skuId);

  @Insert("INSERT INTO cart_item VALUES(#{memberId},#{skuId},#{quantity})")
  int insertForCart(
      @Param("memberId") long memberId,
      @Param("skuId") long skuId,
      @Param("quantity") long quantity);
}
