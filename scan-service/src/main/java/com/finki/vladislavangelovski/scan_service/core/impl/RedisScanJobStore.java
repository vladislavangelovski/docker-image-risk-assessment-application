package com.finki.vladislavangelovski.scan_service.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.scan_service.api.dto.ScanJobStatus;
import com.finki.vladislavangelovski.scan_service.core.ScanJobStore;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisScanJobStore implements ScanJobStore {
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final String keyPrefix = "scan:job:";

  public RedisScanJobStore(StringRedisTemplate redis, ObjectMapper mapper) {
    this.redis = redis;
    this.mapper = mapper;
    System.out.println("[scan-service] Using RedisScanJobStore");
  }

  @Override
  public void put(ScanJobStatus status, Duration ttl) throws StoreWriteException {
    try {
      String key = keyPrefix + status.scanId();
      String json = mapper.writeValueAsString(status);
      redis.opsForValue().set(key, json, ttl);
    } catch (Exception ex) {
      throw new StoreWriteException("Failed to write scan job status to Redis", ex);
    }
  }

  @Override
  public Optional<ScanJobStatus> get(UUID scanId) {
    try {
      String key = keyPrefix + scanId;
      String value = redis.opsForValue().get(key);
      if (value == null || value.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(mapper.readValue(value, ScanJobStatus.class));
    } catch (Exception ex) {
      return Optional.empty();
    }
  }
}
