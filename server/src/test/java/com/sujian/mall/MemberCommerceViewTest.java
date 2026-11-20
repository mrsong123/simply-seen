package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;
import com.sujian.mall.dto.MemberCommerceView;
import java.util.*;
import org.junit.jupiter.api.Test;

class MemberCommerceViewTest {
  @Test
  void memberQuoteHidesCostAndPrivateBudgetWithoutChangingStoredSnapshot() {
    var original = new LinkedHashMap<String,Object>();
    original.put("quoteId","Q1");
    original.put("items", List.of(Map.of("skuId",1,"cost",600,"commissionPercent",7,"cash",12900)));
    original.put("rule",Map.of("level1Bps",500,"commissionBudgetFen",1000000,"beneficiaries",List.of(Map.of("id",42))));
    var result=MemberCommerceView.quote(original);
    assertEquals("Q1",result.get("quoteId"));
    var item=(Map<?,?>)((List<?>)result.get("items")).getFirst();
    assertEquals(12900,item.get("cash"));
    assertFalse(item.containsKey("cost"));assertFalse(item.containsKey("commissionPercent"));
    assertEquals(Map.of("level1Bps",500),result.get("rule"));
    assertTrue(((Map<?,?>)((List<?>)original.get("items")).getFirst()).containsKey("cost"));
    assertTrue(((Map<?,?>)original.get("rule")).containsKey("beneficiaries"));
  }

  @Test
  void ownerOrdersKeepDisplayDataAndHideFinancialAndChannelInternals() {
    var order=new LinkedHashMap<String,Object>();
    order.put("id","SJ1");order.put("payment_channel","NATIVE");
    order.put("payment_payer_openid","private-payer");order.put("payment_app_id","private-app");
    order.put("rule_snapshot","private-rules");order.put("address_snapshot","private-raw-copy");
    order.put("address",Map.of("name","收货人"));
    order.put("items",List.of(Map.of("name","商品","cash_amount",12900,"cost",600,"commission_percent",7)));
    order.put("refunds",List.of(Map.of("state","SUCCESS","amount",12900,"line_snapshot","private-lines")));
    var result=MemberCommerceView.order(order);
    for(String key:List.of("payment_payer_openid","payment_app_id","rule_snapshot","address_snapshot")) assertFalse(result.containsKey(key));
    assertEquals("NATIVE",result.get("payment_channel"));assertEquals(order.get("address"),result.get("address"));
    assertFalse(((Map<?,?>)((List<?>)result.get("items")).getFirst()).containsKey("cost"));
    assertFalse(((Map<?,?>)((List<?>)result.get("refunds")).getFirst()).containsKey("line_snapshot"));
    assertTrue(order.containsKey("rule_snapshot"));
    assertEquals(List.of(result),MemberCommerceView.orders(List.of(order)));
  }

  @Test
  void legacyMemberTeamDoesNotExposeFrozenBudgetSnapshots() {
    var input=Map.of("level1",List.of(),"level2",List.of(),"commissions",List.of(Map.of("amount",645,"rule_snapshot","budget-and-attribution")));
    var view=MemberCommerceView.team(input);
    assertEquals(List.of(Map.of("amount",645)),view.get("commissions"));
    assertTrue(((Map<?,?>)((List<?>)input.get("commissions")).getFirst()).containsKey("rule_snapshot"));
  }
}
