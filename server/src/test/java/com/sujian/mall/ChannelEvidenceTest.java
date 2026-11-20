package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.impl.channel.ChannelEvidenceServiceImpl;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChannelEvidenceTest {
  private final RefundMapper refunds=mock(RefundMapper.class);
  private final WithdrawalMapper withdrawals=mock(WithdrawalMapper.class);
  private final ShopOrderMapper orders=mock(ShopOrderMapper.class);
  private final MemberMapper members=mock(MemberMapper.class);
  private final WechatClient channel=mock(WechatClient.class);
  private ChannelEvidenceServiceImpl evidence;
  @BeforeEach void prepare(){
    Settings settings=new Settings(){@Override public String get(String key,String fallback){return switch(key){case "WX_MCH_ID"->"merchant-test";case "WX_APP_ID"->"app-test";default->fallback;};}};
    evidence=new ChannelEvidenceServiceImpl(refunds,withdrawals,orders,members,settings,channel);
    when(refunds.findForRefund("RF1")).thenReturn(Map.of("order_id","SJ1","amount",300L,"state","PROCESSING"));
    when(orders.selectOrderByNo("SJ1")).thenReturn(Map.of("cash_due",1000L,"transaction_id","payment-transaction"));
    when(withdrawals.findForTransfer("WD1")).thenReturn(Map.of("net",1500L,"member_id",42L));
    when(members.selectAccountById(42)).thenReturn(Map.of("id",42L,"openid","openid-test"));
  }
  private Map<String,Object> refund(){return new HashMap<>(Map.of("out_refund_no","RF1","out_trade_no","SJ1","refund_id","channel-refund","status","SUCCESS","amount",Map.of("refund",300L,"total",1000L,"currency","CNY")));}
  private Map<String,Object> transfer(){return new HashMap<>(Map.of("out_bill_no","WD1","transfer_bill_no","channel-transfer","state","WAIT_USER_CONFIRM","transfer_amount",1500L,"mch_id","merchant-test","appid","app-test","openid","openid-test"));}
  @Test void officialRefundNotificationUsesRefundStatusWithoutPretendingToBeAQuery(){
    var proof=refund();proof.remove("status");proof.put("refund_status","SUCCESS");
    assertEquals("SUCCESS",evidence.refund("RF1",proof).get("status"));
    verifyNoInteractions(channel);
  }
  @Test void incompleteRefundNotificationRequiresAnOriginalSignedQueryInsteadOfInventingCurrency(){
    var proof=refund();proof.remove("status");proof.put("refund_status","SUCCESS");proof.put("amount",Map.of("refund",300L,"total",1000L));
    when(channel.queryRefund("RF1")).thenReturn(refund());
    assertEquals("SUCCESS",evidence.refund("RF1",proof).get("status"));
    verify(channel).queryRefund("RF1");
  }
  @Test void conflictingRefundStateOrAmountIsRejectedBeforeAnyLookupCanWashItAway(){
    var proof=refund();proof.put("refund_status","CLOSED");
    assertThrows(Problem.class,()->evidence.refund("RF1",proof));
    proof.remove("refund_status");proof.put("amount",Map.of("refund",301L,"total",1000L,"currency","CNY"));
    assertThrows(Problem.class,()->evidence.refund("RF1",proof));
    verifyNoInteractions(channel);
  }
  @Test void transferCreationReceiptDoesNotRequireFieldsThatOfficialCreateApiDoesNotReturn(){
    var receipt=Map.<String,Object>of("out_bill_no","WD1","transfer_bill_no","channel-transfer","state","WAIT_USER_CONFIRM","package_info","package-test");
    assertDoesNotThrow(()->evidence.transfer("WD1",receipt,true));
    assertThrows(Problem.class,()->evidence.transfer("WD1",receipt,false));
  }
  @Test void transferQueryAndCallbackValidateRecipientMerchantAndAmount(){
    assertDoesNotThrow(()->evidence.transfer("WD1",transfer(),false));
    for(String field:List.of("mch_id","openid","appid","transfer_amount")){
      var proof=transfer();proof.put(field,field.equals("transfer_amount")?1501L:"wrong-value");
      assertThrows(Problem.class,()->evidence.transfer("WD1",proof,false));
    }
  }
  @Test void knownChannelTransactionCannotBeReplacedByADifferentSignedTransaction(){
    when(withdrawals.findForTransfer("WD1")).thenReturn(Map.of("net",1500L,"member_id",42L,"channel_id","original-channel-id"));
    assertEquals("TRANSFER_CONFLICT",assertThrows(Problem.class,()->evidence.transfer("WD1",transfer(),false)).code);
  }
  @Test void refundMerchantOrPaymentConflictsCannotBeReplacedWithAValidLookup(){
    for(String field:List.of("mchid","mch_id","transaction_id")){
      var proof=refund();proof.put(field,"conflicting-evidence");
      proof.put("amount",Map.of("refund",300L,"total",1000L));
      assertThrows(Problem.class,()->evidence.refund("RF1",proof));
    }
    verifyNoInteractions(channel);
  }
  @Test void originalRefundLookupCannotSilentlyReplaceTheNotificationTransaction(){
    var proof=refund();proof.put("refund_id","different-notification-transaction");
    proof.put("amount",Map.of("refund",300L,"total",1000L));
    when(channel.queryRefund("RF1")).thenReturn(refund());
    assertEquals("REFUND_CONFLICT",assertThrows(Problem.class,()->evidence.refund("RF1",proof)).code);
  }
}
