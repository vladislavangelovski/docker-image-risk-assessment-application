package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComposeConfigFinding(
    String id,
    String title,
    String message,
    String severity,
    String primaryUrl,
    String resource,
    Integer startLine,
    Integer endLine) {}
