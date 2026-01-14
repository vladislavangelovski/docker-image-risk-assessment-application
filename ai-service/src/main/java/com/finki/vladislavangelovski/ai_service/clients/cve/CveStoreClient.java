package com.finki.vladislavangelovski.ai_service.clients.cve;

import com.finki.vladislavangelovski.common.dto.CveForEmbedding;
import com.finki.vladislavangelovski.common.dto.EpssScoreDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CveStoreClient {
  CveForEmbedding getById(String cveId);

  Map<String, CveForEmbedding> getByIds(List<String> cveIds);

  List<CveForEmbedding> findCandidatesForEmbedding(int limit);

  List<CveForEmbedding> findCandidatesForEmbeddingPage(int page, int size);

  Optional<EpssScoreDto> getLatestEpss(String cveId);
}
