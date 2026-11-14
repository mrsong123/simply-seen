package com.sujian.mall.service.impl;

import com.sujian.mall.integration.logistics.LogisticsClient;
import com.sujian.mall.service.LogisticsService;
import com.sujian.mall.service.OrderQueryService;
import org.springframework.stereotype.Service;

@Service
public class LogisticsServiceImpl implements LogisticsService {
  private final OrderQueryService orders;
  private final LogisticsClient channel;

  public LogisticsServiceImpl(OrderQueryService orders, LogisticsClient channel) {
    this.orders = orders;
    this.channel = channel;
  }

  public Object query(long memberId, String orderNo) {
    return channel.query(orders.order(memberId, orderNo));
  }
}
