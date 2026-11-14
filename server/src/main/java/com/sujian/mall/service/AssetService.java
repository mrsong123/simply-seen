package com.sujian.mall.service;

import java.util.*;

public interface AssetService {
  void asset(long uid, String asset, long delta, String event, String order, String note);
}
