package com.sujian.mall.service;

public interface RateLimitService {
  void limit(String bucket, int max);
}
