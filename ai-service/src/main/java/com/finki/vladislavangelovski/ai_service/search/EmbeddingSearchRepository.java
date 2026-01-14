package com.finki.vladislavangelovski.ai_service.search;

import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import java.util.List;

public interface EmbeddingSearchRepository {
  List<SearchHit> search(double[] queryEmbedding, int k);
}
