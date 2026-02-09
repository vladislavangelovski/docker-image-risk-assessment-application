package com.finki.vladislavangelovski.ai_service.clients.scan.dto;

import java.util.Map;

public record ConfigScanSummary(int total, Map<String, Integer> severity) {}
