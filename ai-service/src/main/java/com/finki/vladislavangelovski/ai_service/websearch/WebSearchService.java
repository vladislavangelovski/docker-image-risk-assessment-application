package com.finki.vladislavangelovski.ai_service.websearch;

import java.util.List;
import java.util.Set;

public interface WebSearchService {
  List<WebSearchResult> searchFixes(String question, Set<String> allowedCves);
}
