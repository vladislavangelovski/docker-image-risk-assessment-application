package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessImageResponse(
    String imageRef,
    Integer overallRisk,
    RiskBand band,
    List<TopFinding> topFindings,
    String explanation,
    List<Citation> citations) {}
