package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class BackendMonthlyConsistencyTest extends BackendOperationsFixture {
  @Autowired PlatformTransactionManager transactions;

  @SuppressWarnings("unchecked")
  @Test void refundCannotMissAMonthlyDraftThatIsStillBeingCommitted() throws Exception {
    var product=product(); long sku=n(((List<Map<String,Object>>)product.get("skus")).getFirst(),"id");
    long member=member(); var order=order(member,sku); String no=s(order,"id");
    admin.devPay(member,no);
    YearMonth period=YearMonth.of(2001,1);
    long paid=period.atDay(5).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    // This closed historical period belongs only to this fixture across H2 and shared MySQL runs.
    String month=period.toString();
    jdbc.update("DELETE FROM settlement_award WHERE settlement_id IN (SELECT id FROM settlement WHERE period=?)",month);
    jdbc.update("DELETE FROM settlement WHERE period=?",month);
    jdbc.update("UPDATE shop_order SET paid_at=? WHERE id=?",paid,no);
    var detail=commerce.order(member,no);
    var line=((List<Map<String,Object>>)detail.get("items")).getFirst();
    var refund=(Map<String,Object>)finance.refund(member,"monthly-refund-"+unique(),Map.of("orderNo",no,"reason","退款并发月结验收","lines",List.of(Map.of("orderItemId",n(line,"id"),"quantity",1))));
    finance.reviewRefund(1,s(refund,"id"),true,"核对原单与金额");
    var created=new CountDownLatch(1); var commit=new CountDownLatch(1); var workers=Executors.newFixedThreadPool(2);
    try {
      var drafting=workers.submit(()->new TransactionTemplate(transactions).execute(status->{
        var result=(Map<String,Object>)finance.monthly(1,month);created.countDown();
        try { if(!commit.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Fixture latch timeout"); }
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new RuntimeException(interrupted);}
        return n(result,"id");
      }));
      assertTrue(created.await(10,TimeUnit.SECONDS));
      var reversing=workers.submit(()->finance.refundSuccess(s(refund,"id"),"monthly-channel-"+unique()));
      assertThrows(TimeoutException.class,()->reversing.get(300,TimeUnit.MILLISECONDS));
      commit.countDown(); long settlement=drafting.get(15,TimeUnit.SECONDS);reversing.get(15,TimeUnit.SECONDS);
      assertEquals("INVALIDATED",jdbc.queryForObject("SELECT state FROM settlement WHERE id=?",String.class,settlement));
      assertEquals("REFUNDED",commerce.order(member,no).get("state"));
    } finally {commit.countDown();workers.shutdownNow();}
  }
}
