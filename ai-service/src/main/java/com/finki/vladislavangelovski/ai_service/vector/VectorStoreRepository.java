package com.finki.vladislavangelovski.ai_service.vector;

import com.finki.vladislavangelovski.common.dto.CveForEmbedding;

import java.util.List;

public interface VectorStoreRepository {
    void upsertAll(List<CveForEmbedding> docs, List<float[]> vectors);
    List<SearchHit> search(float[] queryVector, int k);
}
