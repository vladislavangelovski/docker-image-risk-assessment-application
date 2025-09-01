package com.finki.vladislavangelovski.scan_service.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScanProperties.class)
public class ScanConfig {
}
