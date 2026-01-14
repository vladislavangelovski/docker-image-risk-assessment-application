package com.finki.vladislavangelovski.ai_service.clients.scan.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanResult(@JsonProperty("image") String imageRef, List<ScanFinding> findings) {}
