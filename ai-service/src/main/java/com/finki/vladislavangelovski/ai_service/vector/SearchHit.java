package com.finki.vladislavangelovski.ai_service.vector;

public record SearchHit(
        String cveId,
        double distance,
        String title,
        Double epss,
        Double cvssBase
) {
}
