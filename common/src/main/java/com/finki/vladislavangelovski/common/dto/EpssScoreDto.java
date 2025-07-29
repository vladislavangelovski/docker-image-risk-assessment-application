package com.finki.vladislavangelovski.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpssScoreDto {
    private Long id;
    private String cveId;
    private BigDecimal score;
    private BigDecimal percentile;
    private Instant retrievedAt;
}
