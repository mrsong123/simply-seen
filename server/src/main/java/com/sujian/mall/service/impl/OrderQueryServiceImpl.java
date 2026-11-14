package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryServiceImpl implements OrderQueryService {
  @org.springframework.beans.factory.annotation.Autowired private OrderItemMapper orderItemMapper;
  @org.springframework.beans.factory.annotation.Autowired private RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;

  private final BusinessSupport d;

  public OrderQueryServiceImpl(BusinessSupport d) {
    this.d = d;
  }

  public List<Map<String, Object>> orders(long uid) {
    var rows = shopOrderMapper.selectMemberOrders(uid);
    for (var o : rows) o.put("items", orderItemMapper.listForOrders(s(o, "id")));
    return rows;
  }

  public Map<String, Object> order(long uid, String no) {
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.selectOwnedOrder(no, uid));
    o.put("items", orderItemMapper.listForOrders(no));
    o.put("refunds", refundMapper.listForOrder(no));
    o.put("address", d.decode(s(o, "address_snapshot")));
    return o;
  }
}
