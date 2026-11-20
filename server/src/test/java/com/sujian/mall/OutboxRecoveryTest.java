package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import com.sujian.mall.service.impl.OutboxProcessingServiceImpl;
import com.sujian.mall.service.impl.WithdrawalServiceImpl;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRecoveryTest {
  private final WithdrawalMapper withdrawals=mock(WithdrawalMapper.class);
  private final RefundMapper refunds=mock(RefundMapper.class);
  private final MemberMapper members=mock(MemberMapper.class);
  private final WechatClient channel=mock(WechatClient.class);
  private final FinanceService finance=mock(FinanceService.class);
  private final IdentityService identities=mock(IdentityService.class);
  private final ChannelEvidenceService evidence=mock(ChannelEvidenceService.class);
  private final Settings settings=mock(Settings.class);
  private OutboxProcessingServiceImpl processing;
  private final Map<String,Object> withdrawal=new HashMap<>(Map.of("id","WD1","state","SUBMITTED","member_id",42L,"net",1500L));
  @BeforeEach void prepare(){
    when(settings.enabled("TRANSFER_ENABLED")).thenReturn(true);
    processing=new OutboxProcessingServiceImpl(mock(BusinessSupport.class),settings,mock(CommerceService.class),finance,channel,mock(TransactionTemplate.class));
    ReflectionTestUtils.setField(processing,"withdrawalMapper",withdrawals);
    ReflectionTestUtils.setField(processing,"refundMapper",refunds);
    ReflectionTestUtils.setField(processing,"memberMapper",members);
    ReflectionTestUtils.setField(processing,"identities",identities);
    ReflectionTestUtils.setField(processing,"evidence",evidence);
    when(withdrawals.findForTransfer("WD1")).thenReturn(withdrawal);
    when(members.selectAccountById(42)).thenReturn(Map.of("id",42L,"openid","openid-test","debt",0,"blocked",false));
    when(identities.verifiedNameForTransfer(42)).thenReturn("测试姓名");
  }
  private Map<String,Object> receipt(){return Map.of("out_bill_no","WD1","transfer_bill_no","channel-transfer","state","WAIT_USER_CONFIRM","package_info","original-package");}
  @Test void acknowledgedMissingOriginalBillRetriesTheSameMerchantNumberAndVerifiedRecipient(){
    when(channel.queryTransfer("WD1")).thenThrow(new Problem(502,"WX_RESOURCE_NOT_EXISTS","not found"));
    when(channel.transfer(withdrawal,"openid-test","测试姓名")).thenReturn(receipt());
    assertDoesNotThrow(()->processing.transfer("WD1",1));
    verify(channel,times(1)).transfer(withdrawal,"openid-test","测试姓名");
    verify(finance).transferResult("WD1",receipt());
  }
  @Test void manualQueryOfUnapprovedWithdrawalCanNeverTurnIntoANewTransfer(){
    withdrawal.put("state","REVIEWING");
    assertEquals("TRANSFER_NOT_AUTHORIZED",assertThrows(Problem.class,()->processing.transfer("WD1",1)).code);
    verifyNoInteractions(channel,finance,identities);
  }
  @Test void finalCreationReceiptRequiresFullOriginalQueryBeforeFundsAreFinalized(){
    var receipt=Map.<String,Object>of("out_bill_no","WD1","transfer_bill_no","channel-transfer","state","SUCCESS");
    var full=new HashMap<>(receipt);full.put("transfer_amount",1500L);full.put("mch_id","mch");full.put("openid","openid-test");
    when(channel.transfer(withdrawal,"openid-test","测试姓名")).thenReturn(receipt);
    when(channel.queryTransfer("WD1")).thenReturn(full);
    processing.transfer("WD1",0);
    verify(evidence).transfer("WD1",receipt,true);verify(evidence).transfer("WD1",full,false);
    verify(finance).transferResult("WD1",full);
  }
  @Test void queriedWaitStateRetainsOriginalConfirmationPackageInsteadOfErasingIt(){
    var row=new HashMap<String,Object>(Map.of("id","WD1","state","CONFIRMING","package_info","original-package","channel_id","channel-transfer"));
    when(withdrawals.findForReviewWithdrawal("WD1")).thenReturn(row);
    var service=new WithdrawalServiceImpl(mock(BusinessSupport.class),settings,mock(CommerceService.class),identities);
    ReflectionTestUtils.setField(service,"withdrawalMapper",withdrawals);
    service.transferResult("WD1",Map.of("state","WAIT_USER_CONFIRM","transfer_bill_no","channel-transfer"));
    verify(withdrawals).updateForTransferResult2("CONFIRMING","original-package","channel-transfer","WD1");
  }
}
