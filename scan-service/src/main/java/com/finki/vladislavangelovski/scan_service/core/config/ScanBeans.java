package com.finki.vladislavangelovski.scan_service.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.scan_service.core.ScanCache;
import com.finki.vladislavangelovski.scan_service.core.ScanJobCoordinator;
import com.finki.vladislavangelovski.scan_service.core.ScanJobStore;
import com.finki.vladislavangelovski.scan_service.core.ScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.TrivyInvoker;
import com.finki.vladislavangelovski.scan_service.core.TrivyParser;
import com.finki.vladislavangelovski.scan_service.core.impl.*;
import com.finki.vladislavangelovski.scan_service.core.persistence.ScanPersistence;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ScanBeans {
  @Bean
  public TrivyInvoker trivyInvoker(ScanProperties scanProperties) {
    return new ProcessTrivyInvoker(scanProperties);
  }

  @Bean
  public TrivyParser trivyParser() {
    return new JacksonTrivyParser();
  }

  @Bean
  public ScanCache scanCache(Optional<StringRedisTemplate> redisTemplateOpt, ObjectMapper mapper) {
    if (redisTemplateOpt.isPresent()) {
      System.out.println("[scan-service] Using RedisScanCache");
      return new RedisScanCache(redisTemplateOpt.get(), mapper);
    } else {
      System.out.println("[scan-service] Using InMemoryScanCache");
      return new InMemoryScanCache();
    }
  }

  @Bean
  public ScanJobStore scanJobStore(
      Optional<StringRedisTemplate> redisTemplateOpt, ObjectMapper mapper) {
    if (redisTemplateOpt.isPresent()) {
      return new RedisScanJobStore(redisTemplateOpt.get(), mapper);
    }
    return new InMemoryScanJobStore();
  }

  @Bean
  public ScanOrchestrator scanOrchestrator(
      TrivyInvoker trivyInvoker,
      TrivyParser trivyParser,
      ScanCache scanCache,
      ScanProperties scanProperties,
      ScanPersistence scanPersistence) {
    return new DefaultScanOrchestrator(
        trivyInvoker, trivyParser, scanCache, scanProperties, scanPersistence);
  }

  @Bean
  public ScanJobCoordinator scanJobCoordinator(
      ScanOrchestrator scanOrchestrator,
      ScanJobStore scanJobStore,
      TaskExecutor taskExecutor,
      ScanProperties scanProperties) {
    return new DefaultScanJobCoordinator(
        scanOrchestrator, scanJobStore, taskExecutor, scanProperties);
  }
}
