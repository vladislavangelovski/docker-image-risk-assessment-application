package com.finki.vladislavangelovski.scan_service.api.dto;

public record ScanRequest(
        String image,
        RegistryCreds registryCreds,
        ScanOptions options
) {
}
