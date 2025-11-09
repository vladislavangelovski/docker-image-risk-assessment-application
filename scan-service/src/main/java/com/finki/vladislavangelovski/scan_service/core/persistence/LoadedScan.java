package com.finki.vladislavangelovski.scan_service.core.persistence;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Container for a loaded scan: normalized result + optional raw JSON.
 */
public record LoadedScan(
        UUID scanId,
        ScanResult normalized,
        String rawJson
) {
    public Optional<String> rawJsonOptional() {
        return Optional.ofNullable(rawJson);
    }
}
