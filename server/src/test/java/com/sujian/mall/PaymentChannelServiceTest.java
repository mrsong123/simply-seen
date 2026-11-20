package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import com.sujian.mall.common.Problem;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
  "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:paymentChannels;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
  "JOB_INITIAL_DELAY_MS=3600000"
})
@Import(PaymentChannelFixture.Config.class)
class PaymentChannelServiceTest extends PaymentChannelFixture {
  @Test void queryingAnUnstartedPaymentDoesNotQueryANonexistentChannelOrder() {
    var order = createOrder();
    long member = n(order, "member_id");
    String no = s(order, "id");
    shop.synchronizePayment(no);
    assertEquals(0, channel.queries.get());
    assertEquals("INIT", shop.order(member, no).get("payment_state"));
    assertEquals("UNSELECTED", shop.order(member, no).get("payment_channel"));
  }

  @Test void nativeWorksForUnboundPhoneStyleMemberAndIsCachedWithLockedChannel() {
    var order = createOrder(); long member = n(order, "member_id"); String no = s(order, "id");
    var first = shop.prepay(member, no, "NATIVE", "127.0.0.1");
    assertEquals(first, shop.prepay(member, no, "NATIVE", "127.0.0.1"));
    assertEquals(1, channel.calls.get());
    Problem conflict = assertThrows(Problem.class, () -> shop.prepay(member, no));
    assertEquals("PAYMENT_CHANNEL_LOCKED", conflict.code);
    assertEquals("NATIVE", shop.order(member, no).get("payment_channel"));
  }

  @Test void concurrentCreationMakesOneChannelRequestAndLateResultsAreFenced() throws Exception {
    var order = createOrder(); long member = n(order, "member_id"); String no = s(order, "id");
    var executor = Executors.newFixedThreadPool(8);
    try {
      var start = new CountDownLatch(1); List<Future<Boolean>> requests = new ArrayList<>();
      for (int i = 0; i < 8; i++) requests.add(executor.submit(() -> {
        start.await();
        try { shop.prepay(member, no, "NATIVE", "127.0.0.1"); return true; }
        catch (Problem failure) { assertEquals("PAYMENT_PREPARING", failure.code); return false; }
      }));
      start.countDown(); int completed = 0;
      for (var request : requests) if (request.get(15, TimeUnit.SECONDS)) completed++;
      assertTrue(completed >= 1); assertEquals(1, channel.calls.get());
    } finally { executor.shutdownNow(); }
    database.exec("UPDATE payment_session SET state='UNKNOWN',lease_until=0 WHERE order_id=?", no);
    var old = prepayments.reserve(member, no, "NATIVE");
    database.exec("UPDATE payment_session SET lease_until=0 WHERE order_id=?", no);
    var current = prepayments.reserve(member, no, "NATIVE");
    assertThrows(Problem.class, () -> prepayments.complete(no, old.attemptId(), Map.of("mode", "NATIVE")));
    prepayments.complete(no, current.attemptId(), Map.of("mode", "NATIVE"));
    assertEquals(current.attemptId(), database.one("SELECT * FROM payment_session WHERE order_id=?", no).get("attempt_id"));
  }

  @Test void uncertainPrepayQueriesSameMerchantOrderBeforeRetrying() {
    var order = createOrder(); long member = n(order, "member_id"); String no = s(order, "id");
    channel.failNext = true;
    assertThrows(Problem.class, () -> shop.prepay(member, no, "NATIVE", "127.0.0.1"));
    shop.prepay(member, no, "NATIVE", "127.0.0.1");
    assertEquals(1, channel.queries.get()); assertEquals(2, channel.calls.get());
    assertEquals(1, database.count("SELECT COUNT(*) FROM payment_session WHERE order_id=?", no));
    assertEquals(Set.of(no), channel.evidence.keySet());
  }

  @Test void nativeEvidenceStoresActualPayerWithoutPretendingMemberHasWechat() {
    var order = createOrder(); long member = n(order, "member_id"); String no = s(order, "id");
    shop.prepay(member, no, "NATIVE", "127.0.0.1");
    var proof = successfulEvidence(order, "NATIVE", "wx-actual-scanner");
    shop.paymentEvidence(proof); shop.paymentEvidence(proof);
    assertEquals("PAID", shop.order(member, no).get("state"));
    assertEquals("wx-actual-scanner", database.one("SELECT * FROM shop_order WHERE id=?", no).get("payment_payer_openid"));
    assertFalse(shop.order(member, no).containsKey("payment_payer_openid"));
    assertEquals(99, database.count("SELECT stock FROM sku WHERE id=10"));
    assertThrows(Problem.class, () -> shop.paymentEvidence(successfulEvidence(order, "NATIVE", "wx-another-payer")));
  }

  @Test void wrongChannelAmountAndMiniPayerEvidenceCannotSettle() {
    var order = createOrder(); long member = n(order, "member_id"); String no = s(order, "id");
    database.exec("UPDATE member SET openid='wx-member-owner' WHERE id=?", member);
    shop.prepay(member, no, "MINI_PROGRAM", "127.0.0.1");
    assertThrows(Problem.class, () -> shop.paymentEvidence(successfulEvidence(order, "NATIVE", "wx-member-owner")));
    assertThrows(Problem.class, () -> shop.paymentEvidence(successfulEvidence(order, "JSAPI", "wx-wrong-payer")));
    var wrongAmount = new HashMap<>(successfulEvidence(order, "JSAPI", "wx-member-owner"));
    wrongAmount.put("amount", Map.of("total", 1, "currency", "CNY"));
    assertThrows(Problem.class, () -> shop.paymentEvidence(wrongAmount));
    assertEquals("CREATED", shop.order(member, no).get("state"));
    assertEquals(1, database.count("SELECT held FROM sku WHERE id=10"));
    shop.paymentEvidence(successfulEvidence(order, "JSAPI", "wx-member-owner"));
    assertEquals("PAID", shop.order(member, no).get("state"));
  }

  @Test void cancelCannotReleaseStockWhilePrepayMayStillBeCreated() {
    var order = createOrder(); long member = n(order, "member_id"); String no = s(order, "id");
    var reservation = prepayments.reserve(member, no, "NATIVE");
    assertEquals("PAYMENT_PREPARING", assertThrows(Problem.class, () -> shop.cancel(member, no)).code);
    assertEquals(1, database.count("SELECT held FROM sku WHERE id=10"));
    database.exec("UPDATE payment_session SET lease_until=0 WHERE order_id=?", no);
    shop.cancel(member, no);
    assertEquals("CLOSED", shop.order(member, no).get("state"));
    assertEquals(0, database.count("SELECT held FROM sku WHERE id=10"));
    assertThrows(Problem.class, () -> prepayments.complete(no, reservation.attemptId(), Map.of("mode", "NATIVE")));
  }
}
