package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanJobStatus;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanRequest;
import java.util.Optional;
import java.util.UUID;

public interface ScanJobCoordinator {
  ScanJobStatus submit(ScanRequest request) throws ScanJobStore.StoreWriteException;

  Optional<ScanJobStatus> get(UUID scanId);
}
