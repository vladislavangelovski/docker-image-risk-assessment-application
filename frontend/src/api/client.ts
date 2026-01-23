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
import { getAccessToken } from "../auth/token";

const DEFAULT_API_BASE = "http://localhost:8080";

function normalizeBaseUrl(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return "";
  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

function getConfiguredApiBaseUrl(): string | undefined {
  if (typeof window !== "undefined") {
    const runtime = window.__RISK_CONSOLE_CONFIG__?.API_BASE_URL;
    if (runtime) return normalizeBaseUrl(runtime);
  }

  const env = import.meta.env.VITE_API_BASE_URL;
  if (env) return normalizeBaseUrl(env);

  return undefined;
}

export const API_BASE_URL = getConfiguredApiBaseUrl() || DEFAULT_API_BASE;

async function readJson<T>(response: Response): Promise<T> {
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

async function requestJson<T>(
  path: string,
  options: RequestInit = {},
  config: { allowNonOk?: boolean } = {}
): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  const token = getAccessToken();
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers
    });
  } catch {
    throw new Error("Unable to reach the backend API. Check that the gateway is running.");
  }

  if (!response.ok && !config.allowNonOk) {
    const errorText = await response.text().catch(() => "");
    const suffix = errorText ? ` - ${errorText}` : "";
    throw new Error(`${response.status} ${response.statusText}${suffix}`);
  }

  return await readJson<T>(response);
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
