package com.finki.vladislavangelovski.scan_service.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanResult;
import com.finki.vladislavangelovski.scan_service.core.ScanCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RedisScanCache implements ScanCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String keyPrefix = "scan:";
    
    public RedisScanCache(StringRedisTemplate redis,
                          ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
        System.out.println("[scan-service] Using RedisScanCache");
    }
    
    @Override
    public void put(UUID scanId,
                    ScanResult normalized,
                    String rawJson,
                    Duration ttl) throws CacheWriteException {
        try {
            String key = keyPrefix + scanId;
            
            // rawJson can be null (e.g., when raw=false). Use "{}" so cache stays valid.
            String safeRaw = (rawJson == null || rawJson.isBlank()) ? "{}" : rawJson;
            
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("normalized", normalized);
            payload.put("raw", safeRaw);
            
            String json = mapper.writeValueAsString(payload);
            redis.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            throw new CacheWriteException("Failed to write scan to Redis", e);
        }
    }
    
    @Override
    public Optional<CachedScan> get(UUID scanId) {
        try {
            String key = keyPrefix + scanId;
            String value = redis.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            
            JsonNode root = mapper.readTree(value);
            
            JsonNode normNode = root.get("normalized");
            if (normNode == null || normNode.isNull()) {
                return Optional.empty();
            }
            
            ScanResult normalized = mapper.treeToValue(normNode, ScanResult.class);
            
            JsonNode rawNode = root.get("raw");
            String raw = (rawNode == null || rawNode.isNull()) ? "{}" : rawNode.asText("{}");
            
            return Optional.of(new CachedScan(normalized, raw));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
