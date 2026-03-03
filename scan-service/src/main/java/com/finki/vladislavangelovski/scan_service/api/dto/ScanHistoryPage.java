package com.finki.vladislavangelovski.scan_service.api.dto;

import java.util.List;

public record ScanHistoryPage(
    List<ScanHistoryItem> content, int totalPages, long totalElements, int size, int number) {}
