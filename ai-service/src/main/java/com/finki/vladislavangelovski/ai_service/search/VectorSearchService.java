package com.finki.vladislavangelovski.ai_service.search;

import com.finki.vladislavangelovski.ai_service.embeddings.OllamaEmbeddingsClient;
import com.finki.vladislavangelovski.ai_service.search.dto.SearchHit;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class VectorSearchService {
    private final OllamaEmbeddingsClient embeddings;
    private final EmbeddingSearchRepository repo;
    
    private static final double W_COSINE = 0.70;
    private static final double W_EPSS = 0.20;
    private static final double W_CVSS = 0.10;
    
    public VectorSearchService(OllamaEmbeddingsClient embeddings,
                               EmbeddingSearchRepository repo) {
        this.embeddings = embeddings;
        this.repo = repo;
    }
    
    public List<SearchHit> search(String query,
                                  int k) {
        double[] emb = embeddings.embedText(query);
        
        List<SearchHit> rawHits = repo.search(emb, k);
        
        return rawHits.stream().sorted(Comparator.comparingDouble(h -> -combinedScore(h))).toList();
    }
    
    private static double combinedScore(SearchHit h) {
        double cosine = h.similarity();
        if (cosine < 0.0) {
            cosine = 0.0;
        }
        if (cosine > 1.0) {
            cosine = 1.0;
        }
        
        double epss = h.epss() != null ? h.epss() : 0.0;
        
        double cvssNorm = 0.0;
        if (h.cvssBase() != null) {
            cvssNorm = h.cvssBase() / 10.0; // CVSS 0–10 -> 0–1
            if (cvssNorm < 0.0) {
                cvssNorm = 0.0;
            }
            if (cvssNorm > 1.0) {
                cvssNorm = 1.0;
            }
        }
        
        return W_COSINE * cosine + W_EPSS * epss + W_CVSS * cvssNorm;
    }
}