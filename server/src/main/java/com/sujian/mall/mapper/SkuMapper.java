package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Sku;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

  @Select("SELECT * FROM sku WHERE product_id=#{productId} ORDER BY id")
  List<Map<String, Object>> selectCatalogSkus(@Param("productId") long productId);

  @Insert(
      "INSERT INTO sku VALUES(#{id},#{productId},#{name},#{code},#{price},#{cost},#{stock},0,0)")
  int insertCatalogSku(
      @Param("id") long id,
      @Param("productId") long productId,
      @Param("name") String name,
      @Param("code") String code,
      @Param("price") long price,
      @Param("cost") long cost,
      @Param("stock") long stock);

  @Select("SELECT * FROM sku WHERE id=#{id} AND product_id=#{productId} FOR UPDATE")
  Map<String, Object> lockSkuForCatalogUpdate(
      @Param("id") long id, @Param("productId") long productId);

  @Update(
      "UPDATE sku SET"
          + " name=#{name},code=#{code},price=#{price},cost=#{cost},stock=#{stock},version=version+1"
          + " WHERE id=#{id}")
  int updateCatalogSku(
      @Param("name") String name,
      @Param("code") String code,
      @Param("price") long price,
      @Param("cost") long cost,
      @Param("stock") long stock,
      @Param("id") long id);

  @Select(
      "SELECT id,name,price,stock-held AS stock FROM sku WHERE product_id=#{productId} ORDER BY id")
  List<Map<String, Object>> selectPublicSkus(@Param("productId") long productId);

  @Select(
      "SELECT s.id FROM sku s JOIN product p ON p.id=s.product_id WHERE s.id=#{id} AND"
          + " p.status='ON_SALE'")
  Map<String, Object> selectOnSaleSkuId(@Param("id") long id);

  @Select(
      "SELECT s.*,p.name AS product_name,p.image,p.status,p.points_percent,p.commission_percent"
          + " FROM sku s JOIN product p ON s.product_id=p.id WHERE s.id=#{id}")
  Map<String, Object> selectQuotedSku(@Param("id") long id);

  @Select(
      "SELECT s.*,p.status,p.points_percent,p.commission_percent FROM sku s JOIN product p ON"
          + " p.id=s.product_id WHERE s.id=#{id} FOR UPDATE")
  Map<String, Object> selectSkuForReservation(@Param("id") long id);

  @Update(
      "UPDATE sku SET held=held+#{held},version=version+1 WHERE id=#{id} AND stock-held>=#{held2}")
  int reserveStock(@Param("held") long held, @Param("id") long id, @Param("held2") long held2);

  @Update(
      "UPDATE sku SET stock=stock-#{stock},held=held-#{held},version=version+1 WHERE id=#{id} AND"
          + " held>=#{held2} AND stock>=#{stock2}")
  int confirmReservedStock(
      @Param("stock") long stock,
      @Param("held") long held,
      @Param("id") long id,
      @Param("held2") long held2,
      @Param("stock2") long stock2);

  @Update("UPDATE sku SET held=held-#{held},version=version+1 WHERE id=#{id} AND held>=#{held2}")
  int releaseReservedStock(
      @Param("held") long held, @Param("id") long id, @Param("held2") long held2);

  @Update(
      "UPDATE sku SET stock=stock+#{stock},version=version+1 WHERE id=(SELECT sku_id FROM"
          + " order_item WHERE id=#{id})")
  int restoreRefundedStock(@Param("stock") long stock, @Param("id") long id);
}
