package com.finki.vladislavangelovski.scan_service.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ScanRequest(
    @JsonAlias({"image", "imageRef"}) String image,
    RegistryCreds registryCreds,
    ScanOptions options) {}
