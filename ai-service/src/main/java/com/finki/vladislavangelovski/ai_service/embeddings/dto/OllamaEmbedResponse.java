package com.finki.vladislavangelovski.ai_service.embeddings.dto;

import java.util.List;

public record OllamaEmbedResponse(List<List<Double>> embeddings) {
}
