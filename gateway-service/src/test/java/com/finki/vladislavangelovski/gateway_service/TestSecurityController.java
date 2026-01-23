package com.finki.vladislavangelovski.gateway_service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
class TestSecurityController {

  @GetMapping("/api/v1/_test/ping")
  Mono<String> ping() {
    return Mono.just("ok");
  }

  @GetMapping("/api/v1/admin/_test/ping")
  Mono<String> adminPing() {
    return Mono.just("ok");
  }
}

