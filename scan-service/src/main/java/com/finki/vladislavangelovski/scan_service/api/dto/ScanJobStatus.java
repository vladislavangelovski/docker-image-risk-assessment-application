package com.finki.vladislavangelovski.scan_service.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ScanJobStatus(
        UUID scanId,
        String image,
        Status status,
        String message,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }
}
