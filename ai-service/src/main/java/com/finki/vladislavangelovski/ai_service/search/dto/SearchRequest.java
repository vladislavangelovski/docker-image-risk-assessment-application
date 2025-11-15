package com.finki.vladislavangelovski.ai_service.search.dto;

public record SearchRequest(
        String query,
        Integer k
) {
}
