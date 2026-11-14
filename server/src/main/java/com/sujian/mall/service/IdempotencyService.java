package com.sujian.mall.service;

import java.util.*;

public interface IdempotencyService {
  Object idem(
      long uid,
      String operation,
      String key,
      Object body,
      java.util.function.Supplier<Object> action);
}
