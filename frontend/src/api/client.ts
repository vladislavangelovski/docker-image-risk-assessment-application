import type {
  AssessImageRequest,
  AssessImageResponse,
  CveEntry,
  EpssScore,
  Page,
  QaClaimRequest,
  QaClaimResponse,
  QaQuestionRequest,
  QaQuestionResponse
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
    requestJson<EpssScore[]>(`/api/v1/cves/${cveId}/epss?limit=${limit}`)
};
