package com.finki.vladislavangelovski.common.dto;

import com.finki.vladislavangelovski.common.model.Reference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CveEntryDto {
  private String cveId;
  private String source;
  private Instant publishedDate;
  private Instant lastModified;
  private String vulnStatus;
  private List<String> cveTags;

  private String description;
  private List<String> weaknesses;
  private List<Reference> references;

  // CVSS v3.1 flattened
  private String cvssVersion;
  private String cvssVector;
  private BigDecimal cvssBaseScore;
  private String cvssSeverity;
  private String cvssAttackVector;
  private String cvssAttackComplexity;
  private String cvssPrivilegesRequired;
  private String cvssUserInteraction;
  private String cvssScope;
  private String cvssConfidentialityImpact;
  private String cvssIntegrityImpact;
  private String cvssAvailabilityImpact;
  private BigDecimal cvssExploitabilityScore;
  private BigDecimal cvssImpactScore;

  // EPSS
  private BigDecimal epssScore;
  private BigDecimal epssPercentile;

  private Instant ingestTime;
  private Instant lastUpsertTime;
}
