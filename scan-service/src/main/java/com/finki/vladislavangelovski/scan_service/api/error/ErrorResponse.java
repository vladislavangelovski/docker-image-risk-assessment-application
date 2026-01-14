package com.finki.vladislavangelovski.scan_service.api.error;

import java.util.Map;

public record ErrorResponse(ErrorCode errorCode, String message, Map<String, Object> details) {}
