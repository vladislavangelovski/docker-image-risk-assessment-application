package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.ConfigFinding;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import java.util.List;
import java.util.Map;

public interface TrivyConfigParser {
  ParsedConfigScan parse(String rawJson) throws ParserException;

  record ParsedConfigScan(List<ConfigFinding> findings, Map<Severity, Integer> bySeverity) {}
}
