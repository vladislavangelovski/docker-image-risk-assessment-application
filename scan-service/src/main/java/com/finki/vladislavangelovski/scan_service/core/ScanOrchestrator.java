package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanRequest;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import java.util.UUID;

public interface ScanOrchestrator {
  ScanResult scan(ScanRequest request)
      throws ScannerException, ParserException, ScanCache.CacheWriteException;

  default ScanResult scan(ScanRequest request, UUID scanId)
      throws ScannerException, ParserException, ScanCache.CacheWriteException {
    return scan(request);
  }

  boolean exists(UUID scanId);
}
