export type RiskBand = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type Verdict = "SUPPORTS" | "REFUTES" | "INSUFFICIENT";

export interface Citation {
  cveId?: string;
  url?: string;
  snippet?: string;
}

export interface TopFinding {
  cveId?: string;
  epss?: number;
  percentile?: number;
  cvss?: number;
  packages?: string[];
  summary?: string;
  url?: string;
  fixAvailable?: boolean;
}

export interface AssessImageRequest {
  imageRef: string;
  k?: number;
}

export interface AssessImageResponse {
  imageRef?: string;
  overallRisk?: number;
  band?: RiskBand;
  topFindings?: TopFinding[];
  explanation?: string;
  citations?: Citation[];
}

export interface QaQuestionRequest {
  question: string;
  imageRef?: string;
  k?: number;
}

export interface QaQuestionResponse {
  answer?: string;
  citations?: Citation[];
  usedCves?: string[];
  usedPackages?: string[];
}

export interface QaClaimRequest {
  claim: string;
  imageRef?: string;
  topK?: number;
}

export interface QaClaimResponse {
  verdict?: Verdict;
  rationale?: string;
  citations?: Citation[];
}

export interface Reference {
  url?: string;
  source?: string;
}

export interface CveEntry {
  cveId?: string;
  source?: string;
  publishedDate?: string;
  lastModified?: string;
  vulnStatus?: string;
  cveTags?: string[];
  description?: string;
  weaknesses?: string[];
  references?: Reference[];
  cvssVersion?: string;
  cvssVector?: string;
  cvssBaseScore?: number;
  cvssSeverity?: string;
  cvssAttackVector?: string;
  cvssAttackComplexity?: string;
  cvssPrivilegesRequired?: string;
  cvssUserInteraction?: string;
  cvssScope?: string;
  cvssConfidentialityImpact?: string;
  cvssIntegrityImpact?: string;
  cvssAvailabilityImpact?: string;
  cvssExploitabilityScore?: number;
  cvssImpactScore?: number;
  epssScore?: number;
  epssPercentile?: number;
  ingestTime?: string;
  lastUpsertTime?: string;
}

export interface EpssScore {
  id?: number;
  cveId?: string;
  score?: number;
  percentile?: number;
  retrievedAt?: string;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}
