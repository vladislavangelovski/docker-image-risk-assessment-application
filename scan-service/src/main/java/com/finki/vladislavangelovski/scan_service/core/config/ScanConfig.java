package com.finki.vladislavangelovski.scan_service.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
@EnableConfigurationProperties(ScanProperties.class)
public class ScanConfig {
  @Bean
  @ConditionalOnProperty(prefix = "debug.http", name = "log-requests", havingValue = "true")
  public CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
    CommonsRequestLoggingFilter f = new CommonsRequestLoggingFilter();
    f.setIncludeQueryString(true);
    f.setIncludePayload(true);
    f.setMaxPayloadLength(4096); // enough to see your JSON
    f.setIncludeHeaders(false); // avoid noisy headers
    f.setAfterMessagePrefix("REQ >>> ");
    return f;
  }
}
