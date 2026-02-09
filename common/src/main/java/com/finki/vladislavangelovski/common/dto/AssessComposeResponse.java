package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessComposeResponse(
    Integer overallRisk,
    RiskBand band,
    List<ComposeServiceAssessment> services,
    ComposeConfigScan configScan,
    String explanation) {}
