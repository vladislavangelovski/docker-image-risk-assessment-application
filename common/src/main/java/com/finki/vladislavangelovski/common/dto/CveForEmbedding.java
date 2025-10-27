package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CveForEmbedding(
        String cveId,
        String title,
        String description,
        List<String> cwe,
        Double cvssBase,
        String cvssVector,
        Instant published,
        Instant lastModified,
        List<Reference> references,
        Double epss,
        Double epssPercentile
) {
}
