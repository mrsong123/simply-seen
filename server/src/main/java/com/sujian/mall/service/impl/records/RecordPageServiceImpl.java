package com.sujian.mall.service.impl.records;

import com.sujian.mall.common.*;
import com.sujian.mall.dto.*;
import com.sujian.mall.mapper.*;
import com.sujian.mall.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class RecordPageServiceImpl implements RecordPageService {
  private static final Set<String> ASSETS = Set.of("points", "points_held", "available", "reserved", "debt");
  private final RecordPageMapper mapper;
  public RecordPageServiceImpl(RecordPageMapper mapper) { this.mapper = mapper; }

  public RecordPage admin(AuthService.Actor actor, String resource, RecordPageQuery query) {
    var source = RecordPageSource.admin(resource);
    if (!"ADMIN".equals(actor.kind()) || !source.roles().contains(actor.role()))
      throw new Problem(403, "FORBIDDEN", "当前角色不能读取此类记录");
    return page(source, 0, query);
  }

  public RecordPage member(long memberId, String resource, RecordPageQuery query) {
    if (memberId<=0) throw new Problem(401, "AUTH_REQUIRED", "请先登录");
    return page(RecordPageSource.member(resource), memberId, query);
  }

  public RecordPage products(RecordPageQuery query) { return page(RecordPageSource.products(), 0, query); }

  private RecordPage page(RecordPageSource source, long memberId, RecordPageQuery query) {
    validate(source, query);
    var parameters = new HashMap<String, Object>();
    parameters.put("source", source);
    parameters.put("memberId", memberId);
    String search = query.getSearch();
    parameters.put("term", search.isEmpty() ? null : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    parameters.put("state", query.getState());
    parameters.put("asset", query.getAsset());
    parameters.put("categoryId", query.getCategoryId());
    parameters.put("points", query.isPoints());
    parameters.put("sort", query.getSort());
    long total = mapper.count(parameters);
    long totalPages = Math.max(1, (total + query.getSize() - 1) / query.getSize());
    int page = (int)Math.min(query.getPage(), totalPages);
    parameters.put("size", query.getSize());
    parameters.put("offset", (long)(page-1)*query.getSize());
    var items = total==0 ? new ArrayList<Map<String, Object>>() : mapper.rows(parameters);
    enrich(source, items);
    List<String> states = source.state().isEmpty() ? List.of() : mapper.states(parameters).stream()
        .map(row -> Objects.toString(row.get("record_state"), "")).filter(value -> !value.isEmpty()).toList();
    Map<String, Object> summary = Set.of("commissions", "invites", "second-invites").contains(source.kind()) && memberId>0
        ? Map.of("pendingAmount", mapper.pendingCommission(memberId), "level1Count", mapper.directInviteCount(memberId), "level2Count", mapper.secondInviteCount(memberId)) : Map.of();
    return new RecordPage(items, total, page, query.getSize(), totalPages, states, summary);
  }

  private void enrich(RecordPageSource source, List<Map<String, Object>> items) {
    for (var row : items) {
      if (source.enrichment().equals("products")) {
        row.put("skus", mapper.productSkus(BusinessSupport.n(row,"id"), true));
        row.put("retired_skus", mapper.productSkus(BusinessSupport.n(row,"id"), false));
      } else if (source.enrichment().equals("orders")) {
        row.put("items", mapper.orderItems(BusinessSupport.s(row,"id")));
      } else if (source.enrichment().equals("settlements")) {
        row.put("awards", mapper.awards(BusinessSupport.n(row,"id")));
      }
    }
  }

  private void validate(RecordPageSource source, RecordPageQuery query) {
    if (query.getPage()<1 || query.getPage()>1000000 || query.getSize()<1 || query.getSize()>100
        || query.getSearch().length()>100 || !query.getState().matches("[A-Z_]{0,40}")
        || !Set.of("curated","priceUp","priceDown").contains(query.getSort())
        || (query.getCategoryId()!=null && query.getCategoryId()<=0)
        || (!query.getState().isEmpty() && source.state().isEmpty())
        || (!query.getAsset().isEmpty() && (!source.kind().equals("ledger") || !ASSETS.contains(query.getAsset()))))
      throw new Problem(400, "INVALID_PAGE_QUERY", "分页、搜索或筛选参数不正确");
  }
}
