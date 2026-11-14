package com.sujian.mall.service;

import java.util.*;

public interface MonthlySettlementService {
  Object monthly(long actor, String month);

  void approveMonthly(long actor, long id);
}
