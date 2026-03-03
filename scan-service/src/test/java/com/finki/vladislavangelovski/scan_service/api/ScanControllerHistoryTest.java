package com.finki.vladislavangelovski.scan_service.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanHistoryItem;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import com.finki.vladislavangelovski.scan_service.core.ScanCache;
import com.finki.vladislavangelovski.scan_service.core.ScanJobCoordinator;
import com.finki.vladislavangelovski.scan_service.core.ScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.config.ScanProperties;
import com.finki.vladislavangelovski.scan_service.core.persistence.ScanPersistence;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScanControllerHistoryTest {
  private ScanPersistence persistence;
  private ScanController controller;

  @BeforeEach
  void setUp() {
    persistence = mock(ScanPersistence.class);
    controller =
        new ScanController(
            mock(ScanOrchestrator.class),
            mock(ScanCache.class),
            persistence,
            mock(ScanProperties.class),
            mock(ScanJobCoordinator.class));
  }

  @Test
  void historyReturnsPagedResponseWithComputedTotalPages() {
    var item =
        new ScanHistoryItem(
            UUID.randomUUID(),
            "nginx:1.25",
            "Trivy 0.67.2",
            Instant.now().minusSeconds(5),
            Instant.now(),
            12,
            6,
            Severity.HIGH);
    when(persistence.findHistory(0, 50, "nginx", Severity.HIGH))
        .thenReturn(new ScanPersistence.PagedResult<>(List.of(item), 101));

    var response = controller.history(0, 50, "nginx", Severity.HIGH);

    assertThat(response.number()).isEqualTo(0);
    assertThat(response.size()).isEqualTo(50);
    assertThat(response.totalElements()).isEqualTo(101);
    assertThat(response.totalPages()).isEqualTo(3);
    assertThat(response.content()).containsExactly(item);
  }

  @Test
  void historyRejectsNegativePage() {
    assertThatThrownBy(() -> controller.history(-1, 50, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("page must be >= 0");
  }

  @Test
  void historyRejectsOutOfRangeSize() {
    assertThatThrownBy(() -> controller.history(0, 0, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("size must be between 1 and 200");

    assertThatThrownBy(() -> controller.history(0, 201, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("size must be between 1 and 200");
  }
}
