package com.sujian.mall.dto;

import java.util.List;
import java.util.Map;

public record RecordPage(
    List<Map<String, Object>> items, long total, int page, int size, long totalPages,
    List<String> states, Map<String, Object> summary) {}
