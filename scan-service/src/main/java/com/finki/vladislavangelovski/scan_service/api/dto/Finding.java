package com.finki.vladislavangelovski.scan_service.api.dto;

import java.util.List;

public record Finding(
    String cveId,
    String pkg,
    String installedVersion,
    String fixedVersion,
    Severity severity,
    String severitySource,
    Cvss cvss,
    List<String> references,
    String sourceTarget) {}
