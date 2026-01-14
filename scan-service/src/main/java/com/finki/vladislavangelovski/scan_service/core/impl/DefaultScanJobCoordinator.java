package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanJobStatus;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanRequest;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import com.finki.vladislavangelovski.scan_service.core.ScanJobCoordinator;
import com.finki.vladislavangelovski.scan_service.core.ScanJobStore;
import com.finki.vladislavangelovski.scan_service.core.ScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.config.ScanProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;

public class DefaultScanJobCoordinator implements ScanJobCoordinator {
  private static final Logger log = LoggerFactory.getLogger(DefaultScanJobCoordinator.class);
  private final ScanOrchestrator orchestrator;
  private final ScanJobStore store;
  private final TaskExecutor taskExecutor;
  private final ScanProperties properties;

  public DefaultScanJobCoordinator(
      ScanOrchestrator orchestrator,
      ScanJobStore store,
      TaskExecutor taskExecutor,
      ScanProperties properties) {
    this.orchestrator = orchestrator;
    this.store = store;
    this.taskExecutor = taskExecutor;
    this.properties = properties;
  }

  @Override
  public ScanJobStatus submit(ScanRequest request) throws ScanJobStore.StoreWriteException {
    UUID scanId = UUID.randomUUID();
    Instant createdAt = Instant.now();
    ScanJobStatus queued =
        new ScanJobStatus(
            scanId, request.image(), ScanJobStatus.Status.QUEUED, null, createdAt, null, null);
    Duration ttl = Duration.ofSeconds(properties.getJob().getTtlSeconds());
    store.put(queued, ttl);
    taskExecutor.execute(() -> run(scanId, request, createdAt));
    return queued;
  }

  @Override
  public Optional<ScanJobStatus> get(UUID scanId) {
    return store.get(scanId);
  }

  private void run(UUID scanId, ScanRequest request, Instant createdAt) {
    Duration ttl = Duration.ofSeconds(properties.getJob().getTtlSeconds());
    Instant startedAt = Instant.now();
    updateStatus(
        new ScanJobStatus(
            scanId,
            request.image(),
            ScanJobStatus.Status.RUNNING,
            null,
            createdAt,
            startedAt,
            null),
        ttl);
    try {
      ScanResult result = orchestrator.scan(request, scanId);
      Instant finishedAt = result.finishedAt() == null ? Instant.now() : result.finishedAt();
      updateStatus(
          new ScanJobStatus(
              scanId,
              result.image(),
              ScanJobStatus.Status.SUCCEEDED,
              null,
              createdAt,
              startedAt,
              finishedAt),
          ttl);
    } catch (Exception ex) {
      Instant finishedAt = Instant.now();
      log.warn("[scan-service] Async scan failed for scanId={}: {}", scanId, ex.getMessage());
      updateStatus(
          new ScanJobStatus(
              scanId,
              request.image(),
              ScanJobStatus.Status.FAILED,
              "Scan failed",
              createdAt,
              startedAt,
              finishedAt),
          ttl);
    }
  }

  private void updateStatus(ScanJobStatus status, Duration ttl) {
    try {
      store.put(status, ttl);
    } catch (ScanJobStore.StoreWriteException ex) {
      log.warn(
          "[scan-service] Failed to persist scan job status for scanId={}", status.scanId(), ex);
    }
  }
}
