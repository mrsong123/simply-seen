package com.sujian.mall.service;

import com.sujian.mall.dto.*;

public interface RecordPageService {
  RecordPage admin(AuthService.Actor actor, String resource, RecordPageQuery query);
  RecordPage member(long memberId, String resource, RecordPageQuery query);
  RecordPage products(RecordPageQuery query);
}
