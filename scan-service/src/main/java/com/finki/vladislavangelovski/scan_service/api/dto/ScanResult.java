package com.finki.vladislavangelovski.scan_service.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScanResult(
    UUID scanId,
    String image,
    String digest,
    String scannerVersion,
    Instant startedAt,
    Instant finishedAt,
    Summary summary,
    List<Finding> findings) {}
