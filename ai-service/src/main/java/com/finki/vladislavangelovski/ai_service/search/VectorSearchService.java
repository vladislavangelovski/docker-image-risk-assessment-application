package com.finki.vladislavangelovski.ai_service.search;
import com.finki.vladislavangelovski.ai_service.embeddings.OllamaEmbeddingsClient;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorSearchService {
    private final OllamaEmbeddingsClient embeddings;
    private final EmbeddingSearchRepository repo;
    
    public VectorSearchService(OllamaEmbeddingsClient embeddings, EmbeddingSearchRepository repo) {
        this.embeddings = embeddings;
        this.repo = repo;
    }
    
    public List<SearchHit> search(String query, int k) {
        double[] emb = embeddings.embedText(query); // reuse your existing client from B1
        return repo.search(emb, k);
    }
}