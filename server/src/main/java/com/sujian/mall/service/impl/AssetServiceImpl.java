package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AssetServiceImpl implements AssetService {
  @org.springframework.beans.factory.annotation.Autowired private LedgerMapper ledgerMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;

  private final BusinessSupport d;

  public AssetServiceImpl(BusinessSupport d) {
    this.d = d;
  }

  public void asset(long uid, String asset, long delta, String event, String order, String note) {
    Problem.require(
        List.of("points", "points_held", "available", "reserved", "debt").contains(asset),
        "INVALID_ASSET",
        "资产类型无效");
    var m = com.sujian.mall.common.Rows.required(memberMapper.lockAccountById(uid));
    if (ledgerMapper.countForAsset(event) > 0) return;
    long balance = Math.addExact(n(m, asset), delta);
    Problem.require(balance >= 0, "BALANCE_NOT_ENOUGH", "可用资产不足");
    memberMapper.updateAssetBalance(asset, balance, uid);
    ledgerMapper.insertForAsset(d.id(), uid, event, asset, delta, balance, order, note, d.now());
  }
}
