package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanJobStatus;
import com.finki.vladislavangelovski.scan_service.core.ScanJobStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryScanJobStore implements ScanJobStore {
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void put(ScanJobStatus status,
                    Duration ttl) {
        entries.put(status.scanId(), new Entry(status, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<ScanJobStatus> get(UUID scanId) {
        Entry entry = entries.get(scanId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(scanId);
            return Optional.empty();
        }
        return Optional.of(entry.status());
    }

    private record Entry(ScanJobStatus status,
                         Instant expiresAt) {
    }
}
