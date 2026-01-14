package com.finki.vladislavangelovski.gateway_service.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class HttpClientConfig {

  @Bean
  public HttpClient gatewayHttpClient() {
    return HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .responseTimeout(Duration.ofSeconds(180));
  }

  @Bean
  public WebClient webClient(HttpClient gatewayHttpClient) {
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(gatewayHttpClient))
        .build();
  }
}
