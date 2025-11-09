package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import com.finki.vladislavangelovski.scan_service.core.ScanCache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryScanCache implements ScanCache {
    private static final class Entry {
        final ScanResult normalized;
        final String rawJson;
        final Instant expiresAt;
        
        Entry(ScanResult normalized,
              String rawJson,
              Instant expiresAt) {
            this.normalized = normalized;
            this.rawJson = rawJson;
            this.expiresAt = expiresAt;
        }
    }
    
    private final ConcurrentHashMap<UUID, Entry> store = new ConcurrentHashMap<>();
    
    @Override
    public void put(UUID scanId,
                    ScanResult normalized,
                    String rawJson,
                    Duration ttl) throws CacheWriteException {
        try {
            Instant expiry = Instant.now().plus(ttl);
            store.put(scanId, new Entry(normalized, rawJson, expiry));
        } catch (Exception ex) {
            throw new CacheWriteException("Failed to write to in-memory cache", ex);
        }
    }
    
    @Override
    public Optional<CachedScan> get(UUID scanId) {
        Entry entry = store.get(scanId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(scanId);
            return Optional.empty();
        }
        
        return Optional.of(new CachedScan(entry.normalized, entry.rawJson));
    }
}
