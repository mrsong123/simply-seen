package com.sujian.mall.service;

import java.util.Map;

public interface PhoneAuthService {
  Map<String, Object> sendLoginCode(String phone, String ip);

  Map<String, Object> login(String phone, String code, String challengeId, String inviteCode,
      boolean accepted, String ip);

  Map<String, Object> sendBindingCode(long memberId, String phone, String ip);

  Map<String, Object> bindPhone(long memberId, String phone, String code, String challengeId, String ip);

  Map<String, Object> bindWechat(long memberId, String code, String ip);
}
