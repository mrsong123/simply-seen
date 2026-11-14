package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentServiceImpl implements FulfillmentService {
  @org.springframework.beans.factory.annotation.Autowired private RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired private ShopOrderMapper shopOrderMapper;

  private final BusinessSupport d;

  public FulfillmentServiceImpl(BusinessSupport d) {
    this.d = d;
  }

  @Transactional
  public void ship(long admin, String no, Map<String, Object> body) {
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.lockOrderByNo(no));
    Problem.require(
        s(o, "state").equals("PAID") && refundMapper.countForShip(no) == 0,
        "ORDER_STATE",
        "订单不可发货或有进行中的售后");
    Problem.require(
        !s(body, "carrier").isBlank() && s(body, "trackingNo").matches("[A-Za-z0-9-]{5,80}"),
        "INVALID_INPUT",
        "请填写物流公司与有效运单号");
    shopOrderMapper.markShipped(s(body, "carrier"), s(body, "trackingNo"), no);
    d.audit(admin, "SHIP", no, body);
  }

  @Transactional
  public void receive(long uid, String no) {
    var o = com.sujian.mall.common.Rows.required(shopOrderMapper.lockOwnedOrderByNo(no, uid));
    Problem.require(s(o, "state").equals("SHIPPED"), "ORDER_STATE", "订单尚未发货或已收货");
    shopOrderMapper.markCompleted(d.now(), no);
  }
}
