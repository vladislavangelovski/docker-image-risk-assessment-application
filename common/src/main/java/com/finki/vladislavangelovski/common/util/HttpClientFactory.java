package com.finki.vladislavangelovski.common.util;

import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpClientFactory {
  private HttpClientFactory() {}

  public static HttpClient create(Duration connectTimeout) {
    return HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }
}
