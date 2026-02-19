package com.finki.vladislavangelovski.ai_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.finki.vladislavangelovski.ai_service.clients.cve.CveStoreClient;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ConfigFinding;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ConfigScanResult;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ConfigScanSummary;
import com.finki.vladislavangelovski.ai_service.clients.scan.dto.ScanClient;
import com.finki.vladislavangelovski.ai_service.search.VectorSearchService;
import com.finki.vladislavangelovski.ai_service.service.impl.AssessmentServiceImpl;
import com.finki.vladislavangelovski.common.dto.AssessComposeRequest;
import com.finki.vladislavangelovski.common.dto.RiskBand;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssessmentServiceImplComposeTests {

  @Test
  void assessesComposeWithConfigScanAndImageScanningEnabledByDefault() {
    ScanClient scanClient = mock(ScanClient.class);
    when(scanClient.scanDockerCompose(anyString()))
        .thenReturn(
            new ConfigScanResult(
                "docker-compose",
                "Trivy 0.67.2",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"),
                new ConfigScanSummary(3, Map.of("CRITICAL", 1, "LOW", 2)),
                List.of(
                    new ConfigFinding(
                        "DS001",
                        "Privileged container",
                        "Avoid privileged containers.",
                        "Service runs in privileged mode.",
                        "CRITICAL",
                        "https://example.com/DS001",
                        List.of("https://ref.example/1"),
                        "docker-compose.yml",
                        "services.web",
                        10,
                        12))));

    AssessmentServiceImpl service =
        new AssessmentServiceImpl(
            scanClient,
            mock(CveStoreClient.class),
            6,
            10,
            0.65,
            0.35,
            0.15,
            1,
            mock(VectorSearchService.class));

    String composeYaml =
        """
        services:
          web:
            image: nginx:1.25
          worker:
            build:
              context: .
          db: {}
        """;

    var resp = service.assessCompose(new AssessComposeRequest(composeYaml, 6, false));

    assertThat(resp.overallRisk()).isEqualTo(31);
    assertThat(resp.band()).isEqualTo(RiskBand.MEDIUM);
    assertThat(resp.configScan()).isNotNull();
    assertThat(resp.configScan().riskScore()).isEqualTo(31);
    assertThat(resp.services()).hasSize(3);

    var byName =
        resp.services().stream()
            .collect(java.util.stream.Collectors.toMap(s -> s.serviceName(), s -> s));
    assertThat(byName.get("web").assessment()).isNotNull();
    assertThat(byName.get("web").error()).isNull();
    assertThat(byName.get("worker").error()).contains("build");
    assertThat(byName.get("db").error()).contains("no image");

    verify(scanClient).scanDockerCompose(composeYaml);
    verify(scanClient).scanImage("nginx:1.25");
  }

  @Test
  void rejectsInvalidYaml() {
    AssessmentServiceImpl service =
        new AssessmentServiceImpl(
            mock(ScanClient.class),
            mock(CveStoreClient.class),
            6,
            10,
            0.65,
            0.35,
            0.15,
            1,
            mock(VectorSearchService.class));

    assertThatThrownBy(() -> service.assessCompose(new AssessComposeRequest(":\n", 6, false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("valid YAML");
  }
}
