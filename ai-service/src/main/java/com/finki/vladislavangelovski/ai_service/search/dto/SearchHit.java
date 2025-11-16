package com.finki.vladislavangelovski.ai_service.search.dto;

public record SearchHit(
        String cveId,
        String title,
        String description,
        Double epss,
        Double cvssBase,
        Double similarity
) {
}

