package com.finki.vladislavangelovski.scan_service.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.scan_service.core.ScanCache;
import com.finki.vladislavangelovski.scan_service.core.ScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.TrivyInvoker;
import com.finki.vladislavangelovski.scan_service.core.TrivyParser;
import com.finki.vladislavangelovski.scan_service.core.impl.*;
import com.finki.vladislavangelovski.scan_service.core.persistence.ScanPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

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
    public ScanOrchestrator scanOrchestrator(TrivyInvoker trivyInvoker,
                                             TrivyParser trivyParser,
                                             ScanCache scanCache,
                                             ScanProperties scanProperties,
                                             ScanPersistence scanPersistence) {
        return new DefaultScanOrchestrator(
                trivyInvoker, trivyParser, scanCache, scanProperties, scanPersistence);
    }
}
