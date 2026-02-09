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

export interface ComposeConfigFinding {
  id?: string;
  title?: string;
  message?: string;
  severity?: string;
  primaryUrl?: string;
  resource?: string;
  startLine?: number;
  endLine?: number;
}

export interface ComposeConfigScan {
  riskScore?: number;
  totalFindings?: number;
  severity?: Record<string, number>;
  findings?: ComposeConfigFinding[];
  scannerVersion?: string;
  error?: string;
}

export interface ComposeServiceAssessment {
  serviceName?: string;
  imageRef?: string;
  assessment?: AssessImageResponse;
  error?: string;
}

export interface AssessComposeRequest {
  composeYaml: string;
  k?: number;
  scanImages?: boolean;
}

export interface AssessComposeResponse {
  overallRisk?: number;
  band?: RiskBand;
  services?: ComposeServiceAssessment[];
  configScan?: ComposeConfigScan;
  explanation?: string;
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

export type QaChatKind = "QUESTION" | "CLAIM";

export interface QaChatHistoryItem {
  id?: number;
  kind?: QaChatKind;
  prompt?: string;
  imageRef?: string;
  k?: number;
  questionResponse?: QaQuestionResponse;
  claimResponse?: QaClaimResponse;
  createdAt?: string;
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

export type Severity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";

export interface CvssInfo {
  source?: string;
  score?: number;
  vector?: string;
}

export interface ScanFinding {
  cveId?: string;
  packageName?: string;
  installedVersion?: string;
  fixedVersion?: string;
  severity?: Severity;
  severitySource?: string;
  cvss?: CvssInfo;
  references?: string[];
  sourceTarget?: string;
  packages?: string[];
}

export interface ScanSummary {
  total: number;
  severity: Record<Severity, number>;
  fixAvailable: number;
}

export interface ScanResult {
  scanId?: string;
  image?: string;
  digest?: string;
  scannerVersion?: string;
  startedAt?: string;
  finishedAt?: string;
  summary?: ScanSummary;
  findings?: ScanFinding[];
}

export type ScanJobState = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";

export interface ScanJobStatus {
  scanId?: string;
  image?: string;
  status?: ScanJobState;
  message?: string;
  createdAt?: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface EmbeddingsIndexRequest {
  cveIds?: string[];
}

export interface EmbeddingsIndexResponse {
  requested: number;
  upserted: number;
}

export interface EmbeddingsSearchHit {
  cveId?: string;
  similarity?: number;
  title?: string;
  epss?: number;
  cvssBase?: number;
}

export interface EmbeddingsSearchResponse {
  items?: EmbeddingsSearchHit[];
}
