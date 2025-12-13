# End-to-end readiness plan (scan-service → cve-store-service → ai-service)

## What works today (from code)
- **Scan submission**: `ScanController` exposes `/api/v1/scans` POST to run Trivy/Syft via `DefaultScanOrchestrator`, persist via `JdbcScanPersistence`, and cache via Redis (`ScanCache`).【F:scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/ScanController.java†L24-L115】
- **AI assessment**: `AssessmentServiceImpl` calls `ScanClient` for a fresh scan, then enriches each CVE via `CveStoreClient`, scores results, and assembles citations with vector search fallbacks.【F:ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/service/impl/AssessmentServiceImpl.java†L23-L120】
- **Service URLs in compose**: ai-service points at scan-service and cve-store-service over the compose network; Postgres/Redis/Ollama are already wired as dependencies.【F:docker-compose.yml†L83-L125】

## Gaps preventing a reliable end-to-end slice
1. **Gateway is absent** – All traffic must hit services directly; without routes and shared filters, there is no single entry point or consistent auth/observability.
2. **Start-order fragility** – ai-service depends on cve-store and scan-service with `service_started`, so early AI calls can race before those apps are ready; scan-service lacks a healthcheck for compose to wait on.【F:docker-compose.yml†L66-L95】
3. **Scan client fallback calls a non-existent endpoint** – The AI client falls back to GET `/api/v1/scans?imageRef=...`, but scan-service only defines GET by scanId, so the fallback always fails instead of retrying or surfacing the original POST error.【F:ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/clients/scan/ScanClientImpl.java†L24-L41】【F:scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/ScanController.java†L117-L167】
4. **CVE batch retrieval path is wrong for embedding** – `CveStoreClientImpl` derives the list path by replacing `/{id}`, but the configured pattern is `/{cveId}`, so the replacement never happens and the list call hits `/api/v1/cves/{cveId}` (404).【F:ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/clients/cve/impl/CveStoreClientImpl.java†L132-L167】
5. **No smoke test or script for the full flow** – There is no scripted path to run a scan, fetch CVEs/EPSS, and request an AI assessment to validate wiring after changes.

## Priority course of action
1. **Put the gateway in front**
   - Add Spring Cloud Gateway routes for `/api/v1/scans/**`, `/api/v1/cves/**`, and `/api/v1/ai/**`, including request logging, CORS, timeouts, and (optionally) auth.
   - Provide a simple `/health` fan-out or aggregated status to surface downstream readiness.

2. **Stabilize startup and retries**
   - Add healthchecks for scan-service (e.g., `/actuator/health` with DB + Redis indicators) and cve-store; switch compose `depends_on` to `service_healthy` and increase retry/backoff on AI WebClients.
   - Ensure ai-service waits for Postgres migrations; expose Flyway readiness in health probes.

3. **Fix client/API mismatches**
   - Align the AI scan client with scan-service: remove the GET fallback or add a dedicated GET `?imageRef=` endpoint; propagate scan IDs so AI can reuse cached results when provided.
   - Correct `CveStoreClientImpl` list-path derivation (or expose a first-class batch/list endpoint) so embedding/index jobs can pull CVEs without 404s.

4. **Add an end-to-end smoke test**
   - Script a flow that: posts a scan for a small public image, waits/polls for completion (or uses sync response), then calls the AI assessment endpoint and asserts presence of CVE details and citations.
   - Wire the script into CI and a local `make smoke` target to catch wiring regressions.

5. **Close the observability loop**
   - Standardize JSON logging and tracing across scan-service and ai-service clients to capture cross-service latencies; add metrics for failed client calls and ingestion lag.

These steps focus specifically on making the scan → CVE → AI chain reliable with the current code while leaving room for future auth or async/event-driven enhancements.
