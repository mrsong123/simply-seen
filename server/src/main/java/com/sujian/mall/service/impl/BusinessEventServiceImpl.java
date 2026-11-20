package com.sujian.mall.service.impl;

import com.sujian.mall.common.IdBlockAllocator;
import com.sujian.mall.common.JsonCodec;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.BusinessEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessEventServiceImpl implements BusinessEventService {
  @org.springframework.beans.factory.annotation.Autowired private SystemGuardMapper guards;

  private final IdBlockAllocator ids;
  private final JsonCodec json;
  private final AuditMapper auditMapper;
  private final IssueMapper issueMapper;
  private final OutboxMapper outboxMapper;

  public BusinessEventServiceImpl(
      IdBlockAllocator ids,
      JsonCodec json,
      AuditMapper auditMapper,
      IssueMapper issueMapper,
      OutboxMapper outboxMapper) {
    this.ids = ids;
    this.json = json;
    this.auditMapper = auditMapper;
    this.issueMapper = issueMapper;
    this.outboxMapper = outboxMapper;
  }

  public void audit(long actor, String action, String resource, Object detail) {
    auditMapper.insertForAudit(
        ids.next(), actor, action, resource, json.encode(detail), System.currentTimeMillis());
  }

  @Transactional
  public void issue(String kind, String resource, String detail) {
    com.sujian.mall.common.Rows.required(guards.lockIssues());
    var existing = issueMapper.findByBusinessKey(kind, resource);
    if (existing == null)
      issueMapper.insertForIssue(ids.next(), kind, resource, detail, "OPEN", System.currentTimeMillis());
    else if (!"OPEN".equals(existing.get("state")))
      issueMapper.reopen(((Number) existing.get("id")).longValue(), detail, System.currentTimeMillis());
  }

  public void event(String key, String kind, String aggregate) {
    if (outboxMapper.countForEvent(key) == 0) {
      long now = System.currentTimeMillis();
      outboxMapper.insertForEvent(key, kind, aggregate, now, now);
    }
  }
}
