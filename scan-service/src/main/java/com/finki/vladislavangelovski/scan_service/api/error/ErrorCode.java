package com.finki.vladislavangelovski.scan_service.api.error;

public enum ErrorCode {
    BAD_REQUEST_IMAGE,
    MISSING_CREDS,
    REGISTRY_AUTH,
    NOT_FOUND,
    TRIVY_PARSE_ERROR,
    TRIVY_INVOKE,
    TRIVY_SCAN_ERROR,
    TRIVY_TIMEOUT,
    CACHE_WRITE_ERROR,
    INTERNAL
}
