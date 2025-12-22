package com.finki.vladislavangelovski.ai_service.clients.cve;

import com.finki.vladislavangelovski.common.dto.CveForEmbedding;

import java.util.List;
import java.util.Map;

public interface CveStoreClient {
    CveForEmbedding getById(String cveId);
    
    Map<String, CveForEmbedding> getByIds(List<String> cveIds);
    
    List<CveForEmbedding> findCandidatesForEmbedding(int limit);
    
    List<CveForEmbedding> findCandidatesForEmbeddingPage(int page, int size);
    
}
