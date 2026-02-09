package com.finki.vladislavangelovski.scan_service.api.dto;

import java.util.Map;

public record ConfigScanSummary(int total, Map<Severity, Integer> severity) {}
