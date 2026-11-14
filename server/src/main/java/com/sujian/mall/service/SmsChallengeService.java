package com.sujian.mall.service;

import java.util.Map;

public interface SmsChallengeService {
  Map<String, Object> send(String phone, String purpose, long memberId, String ip);

  void consume(String phone, String purpose, long memberId, String challengeId, String code, String ip);
}
