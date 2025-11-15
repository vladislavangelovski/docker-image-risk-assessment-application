package com.finki.vladislavangelovski.ai_service.search.dto;

import java.util.List;

public record SearchResponse(
        int tookMs,
        int k,
        List<SearchHit> hits
) {
}
