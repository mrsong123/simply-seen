package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:backend-operations;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.profiles.active=local", "JOB_INITIAL_DELAY_MS=3600000"
})
@AutoConfigureMockMvc
@Import(BackendOperationsFixture.Configuration.class)
abstract class BackendOperationsFixture {
  @TestConfiguration
  static class Configuration {
    @Bean @Primary Settings settings() {
      return new Settings() {
        @Override public String get(String key, String fallback) {
          return switch (key) {
            case "APP_DEV_MODE" -> "true";
            case "ADMIN_BOOTSTRAP_PASSWORD" -> "backend-fixture-only-password";
            default -> super.get(key, fallback);
          };
        }
      };
    }
  }
  @Autowired BusinessSupport support;
  @Autowired AuthService auth;
  @Autowired AdminService admin;
  @Autowired CommerceService commerce;
  @Autowired FinanceService finance;
  @Autowired SkuMapper skus;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

  String unique() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
  @SuppressWarnings("unchecked")
  long member() {
    return n((Map<String,Object>) auth.devLogin(Map.of("name", "Ops" + unique())).get("profile"), "id");
  }
  Map<String,Object> sku(String code) {
    return new HashMap<>(Map.of("name", "演示规格", "code", code, "price", 10000L, "cost", 2000L, "stock", 20L));
  }
  @SuppressWarnings("unchecked")
  Map<String,Object> product() {
    long category = n((Map<String,Object>)admin.category(0, Map.of("name", "Ops"+unique(), "sort_no", 1)), "id");
    var product = new LinkedHashMap<String,Object>();
    product.put("category_id", category); product.put("name", "Ops"+unique());
    product.put("subtitle", "后台持久化验收"); product.put("description", "测试商品");
    product.put("image", "https://assets.example/item.png"); product.put("color", "#667766");
    product.put("status", "ON_SALE"); product.put("featured", false);
    product.put("points_percent", 20); product.put("commission_percent", 7);
    product.put("skus", List.of(sku(unique()), sku(unique())));
    long id=n((Map<String,Object>)admin.saveProduct(0, product), "id");
    product.put("id",id); product.put("skus",skus.selectCatalogSkus(id));
    return product;
  }
  @SuppressWarnings("unchecked")
  Map<String,Object> order(long memberId, long skuId) {
    long address=n((Map<String,Object>)commerce.address(memberId,Map.of("name","后台测试","phone","13800138000","region","浙江省 杭州市 西湖区","detail","测试路1号","is_default",true)),"id");
    var quote=commerce.quote(memberId,Map.of("items",List.of(Map.of("skuId",skuId,"quantity",1)),"addressId",address,"pointsRequested",0));
    return (Map<String,Object>)commerce.create(memberId,"ops-order-"+unique(),Map.of("quoteId",quote.get("quoteId"),"addressId",address));
  }
  MockHttpServletRequest request(String token) {
    var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer "+token); return request;
  }
}
