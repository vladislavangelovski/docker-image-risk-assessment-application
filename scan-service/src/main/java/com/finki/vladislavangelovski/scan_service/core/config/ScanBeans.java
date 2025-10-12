package com.finki.vladislavangelovski.scan_service.core.config;

import com.finki.vladislavangelovski.scan_service.core.ScanCache;
import com.finki.vladislavangelovski.scan_service.core.ScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.TrivyInvoker;
import com.finki.vladislavangelovski.scan_service.core.TrivyParser;
import com.finki.vladislavangelovski.scan_service.core.impl.DefaultScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.impl.InMemoryScanCache;
import com.finki.vladislavangelovski.scan_service.core.impl.JacksonTrivyParser;
import com.finki.vladislavangelovski.scan_service.core.impl.ProcessTrivyInvoker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public ScanCache scanCache() {
        return new InMemoryScanCache();
    }

    @Bean
    public ScanOrchestrator scanOrchestrator(TrivyInvoker trivyInvoker, TrivyParser trivyParser, ScanCache scanCache, ScanProperties scanProperties) {
        return new DefaultScanOrchestrator(trivyInvoker, trivyParser, scanCache, scanProperties);
    }
}
