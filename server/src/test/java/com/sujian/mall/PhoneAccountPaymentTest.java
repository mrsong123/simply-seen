package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sujian.mall.common.*;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.MemberMapper;
import com.sujian.mall.service.*;
import com.sujian.mall.service.impl.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhoneAccountPaymentTest {
  @Test
  void unboundPhoneAccountCannotMarkOrSubmitMiniProgramPrepayment() {
    for (String openid : new String[] {null, "", "dev:old-test-member"}) {
      var settings = mock(Settings.class);
      var support = mock(BusinessSupport.class);
      var channel = mock(WechatClient.class);
      var queries = mock(OrderQueryService.class);
      var transactions = mock(PrepaymentTransactions.class);
      var members = mock(MemberMapper.class);
      var service = new PaymentInitiationServiceImpl(queries, members, settings, support, channel,
          transactions, mock(PaymentEvidenceService.class), mock(PaymentSettlementService.class));
      when(queries.order(42, "SJ42")).thenReturn(Map.of("state", "CREATED", "expires_at", 2000L));
      when(support.now()).thenReturn(1000L);
      when(members.selectAccountById(42)).thenReturn(openid == null ? Map.of("id", 42L) : Map.of("id", 42L, "openid", openid));
      Problem failure = assertThrows(Problem.class, () -> service.prepay(42, "SJ42", "MINI_PROGRAM", ""));
      assertEquals("WECHAT_BINDING_REQUIRED", failure.code);
      verifyNoInteractions(channel, transactions);
    }
  }
}
