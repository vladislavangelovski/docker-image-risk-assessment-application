import type {
  AssessImageRequest,
  AssessImageResponse,
  CveEntry,
  EmbeddingsIndexRequest,
  EmbeddingsIndexResponse,
  EmbeddingsSearchResponse,
  EpssScore,
  Page,
  QaClaimRequest,
  QaClaimResponse,
  QaQuestionRequest,
  QaQuestionResponse,
  ScanJobStatus,
  ScanResult
} from "./types";

const DEFAULT_API_BASE = "http://localhost:8080";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE;

async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    const errorText = await response.text();
    const suffix = errorText ? ` - ${errorText}` : "";
    throw new Error(`${response.status} ${response.statusText}${suffix}`);
  }

  return (await response.json()) as T;
}

export const api = {
  assessImage: (payload: AssessImageRequest) =>
    requestJson<AssessImageResponse>("/api/v1/assess/image", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  qaQuestion: (payload: QaQuestionRequest) =>
    requestJson<QaQuestionResponse>("/api/v1/qa/question", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  qaClaim: (payload: QaClaimRequest) =>
    requestJson<QaClaimResponse>("/api/v1/qa/claim", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  cveList: (page: number, size: number) =>
    requestJson<Page<CveEntry>>(`/api/v1/cves?page=${page}&size=${size}`),
  cveById: (cveId: string) => requestJson<CveEntry>(`/api/v1/cves/${cveId}`),
  cveEpss: (cveId: string, limit: number) =>
    requestJson<EpssScore[]>(`/api/v1/cves/${cveId}/epss?limit=${limit}`),
  scanById: (scanId: string, raw = false) =>
    requestJson<ScanResult | unknown>(`/api/v1/scans/${scanId}?raw=${raw}`),
  scanLatestByImage: (imageRef: string, raw = false) =>
    requestJson<ScanResult | unknown>(
      `/api/v1/scans?imageRef=${encodeURIComponent(imageRef)}&raw=${raw}`
    ),
  scanJobStatus: (scanId: string) =>
    requestJson<ScanJobStatus>(`/api/v1/scans/jobs/${scanId}`),
  embeddingsIndex: (payload: EmbeddingsIndexRequest) =>
    requestJson<EmbeddingsIndexResponse>("/api/v1/admin/embeddings/index", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  embeddingsSearch: (query: string, k: number) =>
    requestJson<EmbeddingsSearchResponse>(
      `/api/v1/admin/embeddings/search?q=${encodeURIComponent(query)}&k=${k}`
    )
};
