package com.sujian.mall;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sujian.mall.common.BusinessSupport;
import com.sujian.mall.common.Problem;
import com.sujian.mall.config.Settings;
import com.sujian.mall.integration.wechat.WechatClient;
import com.sujian.mall.mapper.MemberMapper;
import com.sujian.mall.mapper.ShopOrderMapper;
import com.sujian.mall.service.AssetService;
import com.sujian.mall.service.OrderQueryService;
import com.sujian.mall.service.impl.PaymentServiceImpl;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

class PhoneAccountPaymentTest {
  @Test
  void unboundPhoneAccountCannotMarkOrSubmitWechatPrepayment() {
    for (String openid : new String[] {null, "", "dev:old-test-member"}) {
      var settings = mock(Settings.class);
      var support = mock(BusinessSupport.class);
      var channel = mock(WechatClient.class);
      var queries = mock(OrderQueryService.class);
      var tx = mock(TransactionTemplate.class);
      var members = mock(MemberMapper.class);
      var orders = mock(ShopOrderMapper.class);
      var service = new PaymentServiceImpl(support, settings, channel, mock(AssetService.class), queries, tx);
      ReflectionTestUtils.setField(service, "memberMapper", members);
      ReflectionTestUtils.setField(service, "shopOrderMapper", orders);
      when(queries.order(42, "SJ42")).thenReturn(Map.of("state", "CREATED", "expires_at", 2000L));
      when(support.now()).thenReturn(1000L);
      when(members.selectAccountById(42)).thenReturn(openid == null ? Map.of("id", 42L) : Map.of("id", 42L, "openid", openid));
      Problem failure = assertThrows(Problem.class, () -> service.prepay(42, "SJ42"));
      assertEquals("WECHAT_BINDING_REQUIRED", failure.code);
      verifyNoInteractions(channel, tx, orders);
    }
  }
}
