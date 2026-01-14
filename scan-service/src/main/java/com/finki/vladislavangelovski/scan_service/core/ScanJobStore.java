package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanJobStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ScanJobStore {
  void put(ScanJobStatus status, Duration ttl) throws StoreWriteException;

  Optional<ScanJobStatus> get(UUID scanId);

  class StoreWriteException extends Exception {
    public StoreWriteException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
