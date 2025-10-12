package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.api.dto.*;
import com.finki.vladislavangelovski.scan_service.core.*;
import com.finki.vladislavangelovski.scan_service.core.config.ScanProperties;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    public ScanResult scan(ScanRequest request) throws ScannerException, ParserException, ScanCache.CacheWriteException {
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

        TrivyOutput output = invoker.run(invocation);

        TrivyParser.ParsedScan parsedScan = parser.parse(output.rawJson());

        List<Finding> findings = parsedScan.findings();
        Map<Severity, Integer> bySeverity = parsedScan.bySeverity();
        int total = findings.size();
        int fixAvailable = parsedScan.fixAvailable();

        Summary summary = new Summary(total, bySeverity, fixAvailable);

        var recomputed = com.finki.vladislavangelovski.scan_service.core.util.ScanValidators.computeSummary(findings);
        if (!com.finki.vladislavangelovski.scan_service.core.util.ScanValidators.matches(summary, recomputed)) {
            summary = recomputed; // correct it silently; optional: log a warning
        }

        Instant finished = Instant.now();

        String image = parsedScan.image() != null ? parsedScan.image() : request.image();
        String digest = parsedScan.digest();

        ScanResult normalized = new ScanResult(
                scanId,
                image,
                digest,
                output.scannerVersion(),
                started,
                finished,
                summary,
                findings
        );

        Duration ttl = Duration.ofSeconds(properties.getCache().getTtlSeconds());
        cache.put(scanId, normalized, output.rawJson(), ttl);

        return normalized;
    }

    @Override
    public boolean exists(UUID scanId) {
        return cache.get(scanId).isPresent();
    }
}
