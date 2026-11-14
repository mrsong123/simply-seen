package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RateLimitServiceImpl implements RateLimitService {
  @org.springframework.beans.factory.annotation.Autowired private RateLimitMapper rateLimitMapper;

  private final BusinessSupport d;
  private final org.springframework.transaction.support.TransactionTemplate limitTx;

  public RateLimitServiceImpl(
      BusinessSupport d, org.springframework.transaction.PlatformTransactionManager tm) {
    this.d = d;
    limitTx = new org.springframework.transaction.support.TransactionTemplate(tm);
    limitTx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void limit(String bucket, int max) {
    limitTx.executeWithoutResult(t -> limitInside(bucket, max));
  }

  private void limitInside(String bucket, int max) {
    long now = d.now();
    var row = rateLimitMapper.listForLimitInside(bucket);
    if (row.isEmpty()) {
      rateLimitMapper.insertForLimitInside(bucket, now);
      return;
    }
    if (now - n(row.getFirst(), "window_at") > 60000) {
      rateLimitMapper.updateForLimitInside(now, bucket);
      return;
    }
    if (n(row.getFirst(), "hits") >= max) throw new Problem(429, "RATE_LIMIT", "操作频繁，请稍后再试");
    rateLimitMapper.updateForLimitInside2(bucket);
  }
}
