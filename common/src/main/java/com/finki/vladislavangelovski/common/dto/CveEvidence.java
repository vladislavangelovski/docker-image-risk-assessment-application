package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CveEvidence(
    String cveId,
    String title,
    String snippet,
    String url,
    Double epss,
    Double percentile,
    Double cvssBase,
    List<String> packages,
    Double scoreCosine,
    Double scoreFinal) {}
