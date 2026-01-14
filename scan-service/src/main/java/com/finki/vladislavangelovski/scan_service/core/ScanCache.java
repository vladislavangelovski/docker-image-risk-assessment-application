package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ScanCache {
  void put(UUID scanId, ScanResult normalized, String rawJson, Duration ttl)
      throws CacheWriteException;

  Optional<CachedScan> get(UUID scanId);

  record CachedScan(ScanResult normalized, String rawJson) {}

  class CacheWriteException extends Exception {
    public CacheWriteException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
