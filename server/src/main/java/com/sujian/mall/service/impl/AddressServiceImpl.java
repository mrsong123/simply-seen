package com.sujian.mall.service.impl;

import static com.sujian.mall.common.BusinessSupport.*;

import com.sujian.mall.common.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressServiceImpl implements AddressService {
  @org.springframework.beans.factory.annotation.Autowired private AddressMapper addressMapper;
  @org.springframework.beans.factory.annotation.Autowired private MemberMapper memberMapper;

  private final BusinessSupport d;

  public AddressServiceImpl(BusinessSupport d) {
    this.d = d;
  }

  public List<Map<String, Object>> addresses(long uid) {
    return addressMapper.listForAddresses(uid);
  }

  @Transactional
  public Object address(long uid, Map<String, Object> b) {
    String phone = s(b, "phone");
    Problem.require(
        s(b, "name").length() > 0
            && s(b, "name").length() <= 60
            && phone.matches("1[3-9][0-9]{9}")
            && !s(b, "region").isBlank()
            && !s(b, "detail").isBlank()
            && s(b, "detail").length() <= 240,
        "INVALID_INPUT",
        "请填写姓名、有效手机号、地区和详细地址");
    long id = n(b, "id");
    com.sujian.mall.common.Rows.required(memberMapper.lockMemberId(uid));
    if (b(b, "is_default")) addressMapper.updateForAddress(uid);
    if (id == 0) {
      id = d.id();
      addressMapper.insertForAddress(
          id, uid, s(b, "name"), phone, s(b, "region"), s(b, "detail"), b(b, "is_default"));
    } else {
      com.sujian.mall.common.Rows.required(addressMapper.findForAddress(id, uid));
      addressMapper.updateForAddress2(
          s(b, "name"), phone, s(b, "region"), s(b, "detail"), b(b, "is_default"), id, uid);
    }
    return Map.of("id", id);
  }
}
