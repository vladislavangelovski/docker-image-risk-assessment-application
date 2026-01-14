package com.finki.vladislavangelovski.scan_service.api.dto;

import java.math.BigDecimal;

public record Cvss(String source, BigDecimal score, String vector) {}
