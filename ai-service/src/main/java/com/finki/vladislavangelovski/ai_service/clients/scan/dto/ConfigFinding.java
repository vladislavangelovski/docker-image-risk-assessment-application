package com.finki.vladislavangelovski.ai_service.clients.scan.dto;

import java.util.List;

public record ConfigFinding(
    String id,
    String title,
    String description,
    String message,
    String severity,
    String primaryUrl,
    List<String> references,
    String target,
    String resource,
    Integer startLine,
    Integer endLine) {}
