package com.finki.vladislavangelovski.ai_service.search.dto;

public record SearchHit(
        String cveId,
        String title,
        String description,
        Double epss,
        Double cvssBase,
        double similarity
        // 0..1 (cosine sim if your index is vector_cosine_ops)
) {
}

