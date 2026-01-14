package com.finki.vladislavangelovski.scan_service.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scan")
public class ScanProperties {
    
    private Defaults defaults = new Defaults();
    private RedisCache cache = new RedisCache();
    private Trivy trivy = new Trivy();
    private Job job = new Job();
    
    public Defaults getDefaults() {
        return defaults;
    }
    
    public RedisCache getCache() {
        return cache;
    }
    
    public Trivy getTrivy() {
        return trivy;
    }

    public Job getJob() {
        return job;
    }
    
    public static class Defaults {
        /**
         * default true
         */
        private boolean ignoreUnfixed = true;
        /**
         * default 120 seconds
         */
        private int timeoutSec = 120;
        
        public boolean isIgnoreUnfixed() {
            return ignoreUnfixed;
        }
        
        public void setIgnoreUnfixed(boolean ignoreUnfixed) {
            this.ignoreUnfixed = ignoreUnfixed;
        }
        
        public int getTimeoutSec() {
            return timeoutSec;
        }
        
        public void setTimeoutSec(int timeoutSec) {
            this.timeoutSec = timeoutSec;
        }
    }
    
    public static class RedisCache {
        /**
         * default 86400 (24h)
         */
        private int ttlSeconds = 86400;
        
        public int getTtlSeconds() {
            return ttlSeconds;
        }
        
        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }
    
    public static class Trivy {
        /**
         * path to trivy binary (inside container or host PATH)
         */
        private String path = "trivy";
        /**
         * disable telemetry inside service containers
         */
        private boolean disableTelemetry = true;
        /**
         * skip version check to reduce network noise
         */
        private boolean skipVersionCheck = true;
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public boolean isDisableTelemetry() {
            return disableTelemetry;
        }
        
        public void setDisableTelemetry(boolean disableTelemetry) {
            this.disableTelemetry = disableTelemetry;
        }
        
        public boolean isSkipVersionCheck() {
            return skipVersionCheck;
        }
        
        public void setSkipVersionCheck(boolean skipVersionCheck) {
            this.skipVersionCheck = skipVersionCheck;
        }
    }

    public static class Job {
        /**
         * default 86400 (24h)
         */
        private int ttlSeconds = 86400;

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }
}
