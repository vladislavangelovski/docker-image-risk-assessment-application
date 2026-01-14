package com.finki.vladislavangelovski.ai_service.embeddings;

import java.util.List;

public interface EmbeddingsClient {
  List<float[]> embedAll(List<String> texts);
}
