package com.finki.vladislavangelovski.scan_service.api.dto;

import java.util.List;

public record ScanOptions(
        Boolean ignoreUnfixed,
        Integer timeoutSec,
        List<String> scanners
) {
}
