package com.sujian.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sujian.mall.entity.Category;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

  @Select("SELECT * FROM category ORDER BY sort_no,id")
  List<Map<String, Object>> listForCategories();

  @Select("SELECT id FROM category WHERE id=#{id}")
  Map<String, Object> findForSaveProduct(@Param("id") long id);

  @Insert("INSERT INTO category VALUES(#{id},#{name},#{sortNo})")
  int insertForCategory(
      @Param("id") long id, @Param("name") String name, @Param("sortNo") long sortNo);

  @Update("UPDATE category SET name=#{name},sort_no=#{sortNo} WHERE id=#{id}")
  int updateForCategory(
      @Param("name") String name, @Param("sortNo") long sortNo, @Param("id") long id);
}
