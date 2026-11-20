package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.dto.RecordPageQuery;
import com.sujian.mall.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:record-pages;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "JOB_INITIAL_DELAY_MS=3600000"})
@Transactional
class RecordPaginationTest {
  @TestConfiguration
  static class Config {
    @Bean @Primary Settings settings() {
      return new Settings() {
        @Override public String get(String key,String fallback) {
          return switch(key) {
            case "APP_DEV_MODE" -> "true";
            case "ADMIN_BOOTSTRAP_PASSWORD" -> "pagination-test-password";
            default -> super.get(key,fallback);
          };
        }
      };
    }
  }
  @Autowired RecordPageService records;
  @Autowired JdbcTemplate jdbc;
  private final AuthService.Actor admin = new AuthService.Actor(1,"ADMIN","ADMIN");

  @BeforeEach
  void seed() {
    for(String table:List.of("member_phone","member_identity","identity_attempt","order_item","payment_session","shop_order","commission","ledger","sku","product","member"))
      jdbc.update("DELETE FROM "+table);
    for(long id:List.of(901L,902L,903L,904L)) {
      Long inviter = null;
      if (id == 903L) inviter = 901L;
      if (id == 904L) inviter = 903L;
      jdbc.update("INSERT INTO member(id,openid,name,invite_code,inviter_id,created_at) VALUES(?,?,?,?,?,?)",
          id,"pagination-"+id,"分页会员"+id,"PAGE"+id,inviter,id);
    }
    for(int index=0;index<245;index++) {
      long id=10000+index;
      jdbc.update("INSERT INTO product(id,category_id,name,subtitle,description,image,color,status,featured,points_percent,commission_percent,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
          id,1,"分页商品"+index,index==0?"含%_特殊字符":"普通商品","商品描述","/art/cup.png","#eee","ON_SALE",false,index%2==0?20:0,7,id);
      jdbc.update("INSERT INTO sku(id,product_id,name,code,price,cost,stock,held,active) VALUES(?,?,?,?,?,?,?,?,?)",
          id,id,"规格","PAGE-SKU-"+id,(long)index+100L,50L,5,1,true);
    }
    jdbc.update("INSERT INTO sku(id,product_id,name,code,price,cost,stock,held,active) VALUES(?,?,?,?,?,?,?,?,?)",
        30000L,10000L,"已停用规格","PAGE-SKU-INACTIVE",1L,1L,999,0,false);
    for(int index=0;index<245;index++) {
      long id=50000+index;
      jdbc.update("INSERT INTO ledger(id,member_id,event_key,asset,amount,balance,order_id,note,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
          id,index==244?902L:901L,"PAGE-EVENT-"+id,index%2==0?"points":"available",1,1,"PAGE-ORDER-"+id,"流水"+index,id);
    }
    for(int index=0;index<21;index++)
      jdbc.update("INSERT INTO commission(id,order_id,beneficiary_id,buyer_id,level_no,amount,original_amount,state,rule_snapshot,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
          80000L+index,"PAGE-"+index,901L,902L,1,100L,100L,index==20?"REVERSED":"PENDING","{}",80000L+index);
  }

  private RecordPageQuery query(int page,int size) {
    var query=new RecordPageQuery();query.setPage(page);query.setSize(size);return query;
  }

  @Test
  void oldRecordsBeyondLegacyLimitsRemainSearchableAndPageable() {
    var page=records.admin(admin,"products",query(25,10));
    assertEquals(245,page.total());assertEquals(25,page.totalPages());assertEquals(5,page.items().size());
    var filter=query(1,10);filter.setSearch("分页商品0");
    var found=records.admin(admin,"products",filter);
    assertEquals(1,found.total());assertEquals(10000L,((Number)found.items().getFirst().get("id")).longValue());
    assertEquals(1,((List<?>)found.items().getFirst().get("skus")).size());
    assertEquals(1,((List<?>)found.items().getFirst().get("retired_skus")).size());
  }

  @Test
  void publicCatalogueCountsAndSortsAllEligibleProductsWithoutInactiveStock() {
    var ascending=query(1,10);ascending.setSort("priceUp");
    var page=records.products(ascending);
    assertEquals(245,page.total());
    assertEquals(100L,((Number)page.items().getFirst().get("price")).longValue());
    assertEquals(4L,((Number)page.items().getFirst().get("stock")).longValue());
    assertFalse(page.items().getFirst().containsKey("cost"));
    assertFalse(page.items().getFirst().containsKey("skus"));
    var descending=query(1,10);descending.setSort("priceDown");
    assertEquals(344L,((Number)records.products(descending).items().getFirst().get("price")).longValue());
    ascending.setPoints(true);
    assertEquals(123,records.products(ascending).total());
  }

  @Test
  void literalWildcardAndInjectionSearchCannotChangeScopeOrSql() {
    var filter=query(1,20);filter.setSearch("%_");
    assertEquals(1,records.products(filter).total());
    filter.setSearch("' OR 1=1 --");
    assertEquals(0,records.products(filter).total());
    assertEquals(1,records.products(filter).totalPages());
    assertThrows(Problem.class,()->records.admin(admin,"member UNION SELECT",filter));
  }

  @Test
  void memberPaginationNeverReturnsAnotherMembersLedgerAndFiltersAssets() {
    var page=records.member(901,"ledger",query(13,20));
    assertEquals(244,page.total());assertEquals(4,page.items().size());
    assertTrue(page.items().stream().allMatch(row->((Number)row.get("member_id")).longValue()==901));
    var points=query(1,100);points.setAsset("points");
    assertEquals(122,records.member(901,"ledger",points).total());
    assertEquals(1,records.member(902,"ledger",query(1,20)).total());
  }

  @Test
  void financialTeamSummaryIsNotLimitedToLoadedRows() {
    var page=records.member(901,"commissions",query(1,1));
    assertEquals(21,page.total());assertEquals(1,page.items().size());
    assertEquals(2000L,((Number)page.summary().get("pendingAmount")).longValue());
    assertEquals(1L,((Number)page.summary().get("level1Count")).longValue());
    assertEquals(1L,((Number)page.summary().get("level2Count")).longValue());
    assertEquals(1,records.member(901,"second-invites",query(1,20)).total());
  }

  @Test
  void rolesBoundQueriesAndPrivateProjectionsStayEnforced() {
    var operator=new AuthService.Actor(2,"ADMIN","OPERATOR");
    assertThrows(Problem.class,()->records.admin(operator,"accounts",query(1,20)));
    assertThrows(Problem.class,()->records.admin(operator,"ledger",query(1,20)));
    assertThrows(Problem.class,()->records.admin(new AuthService.Actor(901,"MEMBER","ADMIN"),"products",query(1,20)));
    var members=records.admin(admin,"members",query(1,20));
    for(var member:members.items()) {
      assertFalse(member.containsKey("openid"));assertFalse(member.containsKey("phone_ciphertext"));
      assertFalse(member.containsKey("password_hash"));assertFalse(member.containsKey("id_number_ciphertext"));
    }
    assertThrows(Problem.class,()->records.products(query(1,101)));
    var invalid=query(1,20);invalid.setState("ON_SALE' OR 1=1");
    assertThrows(Problem.class,()->records.admin(admin,"products",invalid));
    invalid.setState("");invalid.setAsset("password_hash");
    assertThrows(Problem.class,()->records.member(901,"ledger",invalid));
  }
}
