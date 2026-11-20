package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Product;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

  @Select("SELECT * FROM product ORDER BY id DESC LIMIT 200")
  List<Map<String, Object>> listForAdminProducts();

  @Insert(
      "INSERT INTO product"
          + " VALUES(#{id},#{categoryId},#{name},#{subtitle},#{description},#{image},#{color},#{status},#{featured},#{pointsPercent},#{commissionPercent},#{createdAt})")
  int insertForSaveProduct(
      @Param("id") long id,
      @Param("categoryId") long categoryId,
      @Param("name") String name,
      @Param("subtitle") String subtitle,
      @Param("description") String description,
      @Param("image") String image,
      @Param("color") String color,
      @Param("status") String status,
      @Param("featured") boolean featured,
      @Param("pointsPercent") long pointsPercent,
      @Param("commissionPercent") long commissionPercent,
      @Param("createdAt") long createdAt);

  @Select("SELECT id FROM product WHERE id=#{id} FOR UPDATE")
  Map<String, Object> findForSaveProduct(@Param("id") long id);

  @Update(
      "UPDATE product SET"
          + " category_id=#{categoryId},name=#{name},subtitle=#{subtitle},description=#{description},image=#{image},color=#{color},status=#{status},featured=#{featured},points_percent=#{pointsPercent},commission_percent=#{commissionPercent}"
          + " WHERE id=#{id}")
  int updateForSaveProduct(
      @Param("categoryId") long categoryId,
      @Param("name") String name,
      @Param("subtitle") String subtitle,
      @Param("description") String description,
      @Param("image") String image,
      @Param("color") String color,
      @Param("status") String status,
      @Param("featured") boolean featured,
      @Param("pointsPercent") long pointsPercent,
      @Param("commissionPercent") long commissionPercent,
      @Param("id") long id);

  @Select("SELECT COUNT(*) FROM product")
  long countForRun();

  @Select("SELECT * FROM product WHERE id=#{id} AND status='ON_SALE'")
  Map<String, Object> findForProduct(@Param("id") long id);

  @Select({
    "<script>",
    "SELECT p.*, (SELECT MIN(price) FROM sku WHERE product_id=p.id AND active=TRUE) AS price, (SELECT"
        + " SUM(stock-held) FROM sku WHERE product_id=p.id AND active=TRUE) AS stock FROM product p WHERE"
        + " p.status='ON_SALE'",
    "<if test='categoryId != null'> AND p.category_id=#{categoryId}</if>",
    "<if test='search != null and search != &quot;&quot;'> AND p.name LIKE #{search}</if>",
    "ORDER BY p.featured DESC,p.id LIMIT 100",
    "</script>"
  })
  List<Map<String, Object>> search(
      @Param("categoryId") Long categoryId, @Param("search") String search);
}
