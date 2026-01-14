package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopFinding(
    String cveId,
    Double epss,
    Double percentile,
    Double cvss,
    List<String> packages,
    String summary,
    String url,
    Boolean fixAvailable) {}
