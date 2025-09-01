package com.finki.vladislavangelovski.scan_service.api.dto;

import java.util.Map;

public record Summary(
        int total,
        Map<Severity, Integer> severity,
        int fixAvailable
) {
}
