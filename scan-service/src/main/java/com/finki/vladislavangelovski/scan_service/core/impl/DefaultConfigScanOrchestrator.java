package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.api.dto.ConfigFinding;
import com.finki.vladislavangelovski.scan_service.api.dto.ConfigScanResult;
import com.finki.vladislavangelovski.scan_service.api.dto.ConfigScanSummary;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import com.finki.vladislavangelovski.scan_service.core.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class DefaultConfigScanOrchestrator implements ConfigScanOrchestrator {
  private final TrivyConfigInvoker invoker;
  private final TrivyConfigParser parser;

  public DefaultConfigScanOrchestrator(TrivyConfigInvoker invoker, TrivyConfigParser parser) {
    this.invoker = invoker;
    this.parser = parser;
  }

  @Override
  public ConfigScanResult scanDockerCompose(String composeYaml, Duration timeout)
      throws ScannerException, ParserException {
    return scanSingleFile("docker-compose", "docker-compose.yml", composeYaml, timeout);
  }

  @Override
  public ConfigScanResult scanDockerfile(String dockerfile, Duration timeout)
      throws ScannerException, ParserException {
    return scanSingleFile("dockerfile", "Dockerfile", dockerfile, timeout);
  }

  private ConfigScanResult scanSingleFile(
      String kind, String fileName, String content, Duration timeout)
      throws ScannerException, ParserException {
    Path tmpDir = null;
    try {
      tmpDir = Files.createTempDirectory("trivy-config-");
      Path input = tmpDir.resolve(fileName);
      Files.writeString(input, content, StandardCharsets.UTF_8);

      Instant started = Instant.now();
      TrivyConfigOutput output = invoker.run(new TrivyConfigInvocationRequest(input, timeout));
      TrivyConfigParser.ParsedConfigScan parsed = parser.parse(output.rawJson());
      Instant finished = Instant.now();

      Map<Severity, Integer> bySeverity = ensureAllSeverities(parsed.bySeverity());
      List<ConfigFinding> findings = parsed.findings();

      ConfigScanSummary summary =
          new ConfigScanSummary(findings != null ? findings.size() : 0, bySeverity);
      return new ConfigScanResult(
          kind,
          output.scannerVersion(),
          started,
          finished,
          summary,
          findings != null ? findings : List.of());
    } catch (IOException e) {
      throw new ScannerException("Failed to create temp file for config scan", e);
    } finally {
      if (tmpDir != null) {
        deleteRecursively(tmpDir);
      }
    }
  }

  private static Map<Severity, Integer> ensureAllSeverities(Map<Severity, Integer> input) {
    Map<Severity, Integer> map = new EnumMap<>(Severity.class);
    for (Severity s : Severity.values()) {
      map.put(s, 0);
    }
    if (input != null) {
      for (var e : input.entrySet()) {
        if (e.getKey() != null && e.getValue() != null) {
          map.put(e.getKey(), e.getValue());
        }
      }
    }
    return map;
  }

  private static void deleteRecursively(Path dir) {
    try (Stream<Path> paths = Files.walk(dir)) {
      paths
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // best-effort cleanup
                }
              });
    } catch (IOException ignored) {
      // best-effort cleanup
    }
  }
}
