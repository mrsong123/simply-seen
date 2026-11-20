package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.sujian.mall.common.Problem;
import java.util.*;
import org.junit.jupiter.api.Test;

class BackendCatalogPersistenceTest extends BackendOperationsFixture {
  @SuppressWarnings("unchecked")
  @Test void removingSpecificationRetiresItAndPreservesItsHistoricalStock() {
    var product=product();
    var specifications=(List<Map<String,Object>>)product.get("skus");
    long retired=n(specifications.getFirst(),"id"), member=member();
    commerce.cart(member,retired,1);
    var paid=order(member,retired);
    admin.devPay(member,s(paid,"id"));
    commerce.cart(member,retired,1);
    product.put("skus",List.of(specifications.get(1)));
    admin.saveProduct(0,product);
    assertFalse(skus.selectById(retired).getActive());
    assertEquals(19,skus.selectById(retired).getStock());
    assertEquals(1,skus.selectPublicSkus(n(product,"id")).size());
    assertEquals(1,skus.selectRetiredSkus(n(product,"id")).size());
    var cart=commerce.cart(member).getFirst();
    assertEquals("OFF_SALE",s(cart,"status")); assertEquals(0,n(cart,"stock"));
    assertThrows(Problem.class,()->commerce.cart(member,retired,1));
    assertNotNull(commerce.order(member,s(paid,"id")).get("items"));
    var detail=commerce.order(member,s(paid,"id"));
    var item=((List<Map<String,Object>>)detail.get("items")).getFirst();
    var refund=(Map<String,Object>)finance.refund(member,"ops-refund-"+unique(),Map.of("orderNo",s(paid,"id"),"reason","停用规格仍可退款","lines",List.of(Map.of("orderItemId",n(item,"id"),"quantity",1))));
    finance.reviewRefund(0,s(refund,"id"),true,"原单核验通过");
    finance.refundSuccess(s(refund,"id"),"REFUND-"+unique());
    assertEquals(20,skus.selectById(retired).getStock());
    assertFalse(skus.selectById(retired).getActive());
  }

  @SuppressWarnings("unchecked")
  @Test void reservedSpecificationCannotBeRemovedOrHidePartialProductChanges() {
    var product=product(); var specifications=(List<Map<String,Object>>)product.get("skus");
    long reserved=n(specifications.getFirst(),"id"), member=member();
    order(member,reserved);
    product.put("skus",List.of(specifications.get(1))); product.put("name","不应保存的修改");
    Problem failure=assertThrows(Problem.class,()->admin.saveProduct(0,product));
    assertEquals("STOCK_HELD",failure.code);
    assertEquals(2,skus.selectCatalogSkus(n(product,"id")).size());
    assertNotEquals("不应保存的修改",jdbc.queryForObject("SELECT name FROM product WHERE id=?",String.class,n(product,"id")));
  }

  @SuppressWarnings("unchecked")
  @Test void duplicateSpecificationPayloadCannotSilentlyOverwriteTheSameRecordTwice() {
    var product=product(); var specifications=(List<Map<String,Object>>)product.get("skus");
    product.put("skus",List.of(specifications.getFirst(),specifications.getFirst()));
    assertThrows(Problem.class,()->admin.saveProduct(0,product));
    assertEquals(2,skus.selectCatalogSkus(n(product,"id")).size());
  }
}
