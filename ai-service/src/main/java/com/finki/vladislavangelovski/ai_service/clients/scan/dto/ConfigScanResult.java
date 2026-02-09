package com.finki.vladislavangelovski.ai_service.clients.scan.dto;

import java.time.Instant;
import java.util.List;

public record ConfigScanResult(
    String kind,
    String scannerVersion,
    Instant startedAt,
    Instant finishedAt,
    ConfigScanSummary summary,
    List<ConfigFinding> findings) {}
