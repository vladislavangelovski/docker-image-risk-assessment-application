package com.finki.vladislavangelovski.scan_service.core;

import com.finki.vladislavangelovski.scan_service.api.dto.RegistryCreds;

import java.time.Duration;
import java.util.List;

public record TrivyInvocationRequest (
        String image,
        boolean ignoreUnfixed,
        Duration timeout,
        List<String> scanners,
        RegistryCreds registryCreds
) {
}
