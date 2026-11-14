package com.sujian.mall.service;

/** Application boundary for authorized order logistics queries. */
public interface LogisticsService {
  Object query(long memberId, String orderNo);
}
