package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.ConfigScanResult;
import java.time.Duration;

public interface ConfigScanOrchestrator {
  ConfigScanResult scanDockerCompose(String composeYaml, Duration timeout)
      throws ScannerException, ParserException;

  ConfigScanResult scanDockerfile(String dockerfile, Duration timeout)
      throws ScannerException, ParserException;
}
