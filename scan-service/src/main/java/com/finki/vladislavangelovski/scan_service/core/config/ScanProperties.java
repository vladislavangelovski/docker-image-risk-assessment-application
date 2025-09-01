package com.finki.vladislavangelovski.scan_service.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scan")
@Getter
public class ScanProperties {
    private Defaults defaults = new Defaults();
    private RedisCache cache = new RedisCache();
    private Trivy trivy = new Trivy();

    @Getter
    @Setter
    public static class Defaults {
        /** default true */
        private boolean ignoreUnfixed = true;
        /** default 120 seconds */
        private int timeoutSec = 120;
    }

    @Getter
    @Setter
    public static class RedisCache {
        /** default 86400 (24h) */
        private int ttlSeconds = 86400;
    }

    @Getter
    @Setter
    public static class Trivy {
        /** path to trivy binary (inside container or host PATH) */
        private String path = "trivy";
        /** disable telemetry inside service containers */
        private boolean disableTelemetry = true;
        /** skip version check to reduce network noise */
        private boolean skipVersionCheck = true;
    }
}
