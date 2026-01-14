package com.finki.vladislavangelovski.scan_service.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanJobStatus;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanRequest;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import com.finki.vladislavangelovski.scan_service.api.dto.Summary;
import com.finki.vladislavangelovski.scan_service.core.ScanJobStore;
import com.finki.vladislavangelovski.scan_service.core.ScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.config.ScanProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.task.TaskExecutor;

class DefaultScanJobCoordinatorTest {
  @Test
  void submitRunsJobAndUpdatesStatus() throws Exception {
    ScanOrchestrator orchestrator = Mockito.mock(ScanOrchestrator.class);
    ScanJobStore store = new InMemoryScanJobStore();
    TaskExecutor executor = Runnable::run;
    ScanProperties properties = new ScanProperties();
    properties.getJob().setTtlSeconds(60);

    ScanRequest request = new ScanRequest("nginx:1.25", null, null);
    Mockito.when(orchestrator.scan(Mockito.eq(request), Mockito.any(UUID.class)))
        .thenAnswer(
            invocation -> {
              UUID scanId = invocation.getArgument(1);
              Instant now = Instant.now();
              return new ScanResult(
                  scanId,
                  "nginx:1.25",
                  null,
                  "Trivy",
                  now,
                  now,
                  new Summary(0, Map.of(), 0),
                  List.of());
            });

    DefaultScanJobCoordinator coordinator =
        new DefaultScanJobCoordinator(orchestrator, store, executor, properties);

    ScanJobStatus submitted = coordinator.submit(request);

    assertEquals(ScanJobStatus.Status.QUEUED, submitted.status());
    assertTrue(store.get(submitted.scanId()).isPresent());
    assertEquals(ScanJobStatus.Status.SUCCEEDED, store.get(submitted.scanId()).get().status());
  }
}
