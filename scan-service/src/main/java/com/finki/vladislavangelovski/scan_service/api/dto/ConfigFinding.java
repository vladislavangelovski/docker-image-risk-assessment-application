package com.finki.vladislavangelovski.scan_service.api.dto;

import java.util.List;

public record ConfigFinding(
    String id,
    String title,
    String description,
    String message,
    Severity severity,
    String primaryUrl,
    List<String> references,
    String target,
    String resource,
    Integer startLine,
    Integer endLine) {}
