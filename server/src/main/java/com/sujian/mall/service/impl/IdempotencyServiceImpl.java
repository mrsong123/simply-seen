package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {
  @org.springframework.beans.factory.annotation.Autowired
  private IdempotencyMapper idempotencyMapper;

  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;

  private final BusinessSupport d;

  public IdempotencyServiceImpl(BusinessSupport d) {
    this.d = d;
  }

  public Object idem(
      long uid,
      String operation,
      String key,
      Object body,
      java.util.function.Supplier<Object> action) {
    Problem.require(
        key != null && key.matches("[A-Za-z0-9_-]{8,80}"), "IDEMPOTENCY_REQUIRED", "请提供8至80位幂等键");
    com.sujian.mall.common.Rows.required(memberMapper.lockMemberId(uid));
    String scope = uid + ":" + operation + ":" + key, hash = hash(d.encode(body));
    var rows = idempotencyMapper.listForIdem(scope);
    if (!rows.isEmpty()) {
      Problem.require(
          s(rows.getFirst(), "payload_hash").equals(hash),
          "IDEMPOTENCY_CONFLICT",
          "相同请求编号不能提交不同内容");
      return d.decode(s(rows.getFirst(), "response"));
    }
    Object response = action.get();
    idempotencyMapper.insertForIdem(scope, hash, d.encode(response), d.now());
    return response;
  }
}
