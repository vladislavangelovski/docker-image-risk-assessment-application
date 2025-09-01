package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.api.dto.ScanRequest;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import com.finki.vladislavangelovski.scan_service.core.*;
import com.finki.vladislavangelovski.scan_service.core.config.ScanProperties;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


public class DefaultScanOrchestrator implements ScanOrchestrator {
    private final TrivyInvoker invoker;
    private final TrivyParser parser;
    private final ScanCache cache;
    private final ScanProperties properties;

    public DefaultScanOrchestrator(TrivyInvoker invoker, TrivyParser parser, ScanCache cache, ScanProperties props) {
        this.invoker = invoker;
        this.parser = parser;
        this.cache = cache;
        this.properties = props;
    }

    @Override
    public ScanResult scan(ScanRequest request) throws ScannerException {
        boolean ignoreUnfixed = request.options() == null || request.options().ignoreUnfixed() == null
                ? properties.getDefaults().isIgnoreUnfixed()
                : request.options().ignoreUnfixed();

        int timeoutSec = request.options() == null || request.options().timeoutSec() == null
                ? properties.getDefaults().getTimeoutSec()
                : request.options().timeoutSec();

        var invocation = new TrivyInvocationRequest(
                request.image(),
                ignoreUnfixed,
                Duration.ofSeconds(timeoutSec),
                List.of("vuln"),
                request.registryCreds()
        );

        UUID scanId = UUID.randomUUID();
        Instant started = Instant.now();

        throw new UnsupportedOperationException("DefaultScanOrchestrator.scan not implemented yet");
    }

    @Override
    public boolean exists(UUID scanId) {
        return cache.get(scanId).isPresent();
    }
}
