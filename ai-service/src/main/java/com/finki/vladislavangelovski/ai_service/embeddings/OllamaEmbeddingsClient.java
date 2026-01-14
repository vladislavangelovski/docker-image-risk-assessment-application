package com.finki.vladislavangelovski.ai_service.embeddings;

import com.finki.vladislavangelovski.ai_service.embeddings.dto.OllamaEmbedResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OllamaEmbeddingsClient implements EmbeddingsClient {
  private final WebClient client;
  private final String model;
  private final int expectedDim;

  public OllamaEmbeddingsClient(
      @Qualifier("embeddingsWebClient") WebClient client,
      @Value("${embeddings.model}") String model,
      @Value("${embeddings.expected-dim}") int expectedDim) {
    this.client = client;
    this.model = model;
    this.expectedDim = expectedDim;
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }

    var req = Map.of("model", model, "input", texts);
    OllamaEmbedResponse resp =
        client
            .post()
            .uri("/api/embed")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(req)
            .retrieve()
            .bodyToMono(OllamaEmbedResponse.class)
            .block();

    if (resp == null || resp.embeddings() == null) {
      return List.of();
    }

    List<float[]> out = new ArrayList<>(resp.embeddings().size());
    for (List<Double> row : resp.embeddings()) {
      float[] v = new float[row.size()];
      for (int i = 0; i < row.size(); i++) {
        v[i] = row.get(i).floatValue();
      }
      if (expectedDim > 0 && v.length != expectedDim) {
        throw new IllegalStateException(
            "Embedding dim " + v.length + " != expected " + expectedDim);
      }
      out.add(v);
    }
    return out;
  }

  public double[] embedText(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("text must be non-empty");
    }
    var list = embedAll(Collections.singletonList(text));
    if (list.isEmpty()) {
      throw new IllegalStateException("Ollama returned no embeddings");
    }
    float[] f = list.get(0);
    double[] d = new double[f.length];
    for (int i = 0; i < f.length; i++) {
      d[i] = f[i];
    }
    if (expectedDim > 0 && d.length != expectedDim) {
      throw new IllegalStateException("Embedding dim " + d.length + " != expected " + expectedDim);
    }
    return d;
  }
}
