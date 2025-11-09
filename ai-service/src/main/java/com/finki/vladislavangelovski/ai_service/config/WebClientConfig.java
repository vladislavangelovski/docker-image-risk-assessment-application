package com.finki.vladislavangelovski.ai_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient scanWebClient(@Value("${services.scan.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(ExchangeStrategies.builder()
                                            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                                            .build())
                .build();
    }
    
    @Bean
    public WebClient cveStoreWebClient(@Value("${services.cvestore.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(ExchangeStrategies.builder()
                                            .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                                            .build())
                .build();
    }
}
