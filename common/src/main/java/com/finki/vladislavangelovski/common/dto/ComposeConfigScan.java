package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComposeConfigScan(
    Integer riskScore,
    Integer totalFindings,
    Map<String, Integer> severity,
    List<ComposeConfigFinding> findings,
    String scannerVersion,
    String error) {}
