package com.finki.vladislavangelovski.scan_service.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ScanHistoryItem(
    UUID scanId,
    String image,
    String scannerVersion,
    Instant startedAt,
    Instant finishedAt,
    int totalFindings,
    int fixAvailable,
    Severity maxSeverity) {}
