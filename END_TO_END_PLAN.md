# End-to-end plan (roadmap-mapped checklist)

This file mirrors `ROADMAP.md` and tracks implementation status based on what exists in this repository today (code +
config). Keep it updated as phases are completed.

This is **not** a release note. A checked item (`[x]`) means “implemented in-repo and referenced by config”, not
necessarily “hardened and proven for production”. Use the **Production readiness gate** section below as the
“ship/no-ship” checklist.

Legend:
- `- [x]` done in repo
- `- [ ]` missing / needs work (or only partially done)

## Production readiness gate (ship/no-ship)
- [x] Secure-by-default gateway edge (CORS deny-all + trusted proxies allowlist) with `dev` profile override (see `gateway-service/src/main/resources/application.yml` and `gateway-service/src/main/resources/application-dev.yml`)
- [ ] Authentication + authorization at the gateway (and protect admin endpoints)
- [ ] Rate limiting + request size limits at the gateway (per-route; include scan and QA endpoints)
- [x] Correlation/request ID propagation end-to-end (gateway → services)
- [ ] Structured JSON logging with redaction rules
- [ ] Metrics export (Prometheus) + dashboards/alerts (ingestion lag, scan duration, LLM latency/error rates, DB/Redis)
- [ ] Distributed tracing (OpenTelemetry) with sampling and service-to-service context propagation
- [ ] Health/readiness probes for **all** services (compose and Kubernetes), including `ai-service` dependency checks (DB + Ollama)
- [ ] Secrets/config strategy for production (no committed `.env` — currently committed; Kubernetes Secrets/ConfigMaps; rotation + least privilege)
- [ ] Backup/restore runbook for PostgreSQL (including pgvector) + retention policy for raw scan outputs
- [ ] CI security gates: dependency SCA, container scanning, SBOM generation (and optional image signing)
- [ ] Kubernetes baseline: manifests/Helm, resource requests/limits, network policies, ingress/TLS, and environment profiles
- [ ] Production profiles documented (CORS allowlist, logging levels, timeouts, model/provider config, feature flags)
- [ ] Overload protection (scan/LLM concurrency limits, bulkheads, timeouts) and graceful degradation behavior documented

## Phase 0 – Define & Align Requirements & Scope
- [ ] Revisit functional requirements: scan images, ingest CVE/EPSS, RAG QA (beyond `ROADMAP.md`/`README.md`)
- [ ] Revisit non-functional requirements: performance, scalability, security, reliability, cost (beyond `ROADMAP.md`)
- [ ] Draft minimal API spec (gateway-first) as a stable external contract
  - [x] claim/question in → summary + evidence out (gateway: `POST /api/v1/qa/*`, downstream: `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/QaController.java`)
  - [x] assess image in → risk summary + findings out (gateway: `POST /api/v1/assess/image`, downstream: `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/AssessmentController.java`)
- [ ] API versioning + deprecation policy (keep `/api/v1` stable; define how aliases are deprecated)
- [x] Tech-stack confirmation: Java 21 + Spring Boot 3.x (see `pom.xml`)
- [x] Maven multi-module layout (modules exist: `common`, `cve-store-service`, `scan-service`, `ai-service`, `gateway-service`) (see `pom.xml`)
- [x] Redis + PostgreSQL wired (see `docker-compose.yml`)
- [x] Docker Compose (local) (see `docker-compose.yml`)
- [ ] Kubernetes (production target) tracked in repo (no manifests/helm yet)
- [x] Spring Boot Actuator enabled (health endpoints; compose healthchecks for Postgres/Redis/CVE Store/Scan/Gateway — `ai-service` has no healthcheck yet) (see `docker-compose.yml`)
- [ ] Micrometer metrics export configured (no Prometheus registry/config yet; metrics endpoints not exposed)
- [x] GitHub Actions CI/CD present (see `.github/workflows/ci.yml`)

## Phase 1 – Repository & Project Skeleton
- [x] Git repository exists (see `.git/`)
- [ ] Branch strategy documented (no `CONTRIBUTING.md` / documented workflow yet)
- [x] Parent POM with shared properties (Java 21) and modules (see `pom.xml`)
- [x] GitHub Actions workflow runs Maven build/verify on PRs (see `.github/workflows/ci.yml`)

## Phase 2 – “common” Module
- [x] Shared DTOs/models exist (see `common/src/main/java/com/finki/vladislavangelovski/common/dto`)
- [x] Common utilities: HTTP clients, exception wrappers, shared error model, validation helpers (centralized in `common`)
- [x] Common Jackson configuration and version constants (centralized in `common`)
- [x] DependencyManagement for consistent library versions (see `pom.xml`)

## Phase 3 – cve-store-service
- [x] Ingestion pipeline for NVD CVEs and EPSS CSV (scheduled pulls) (see `cve-store-service/src/main/java/com/finki/vladislavangelovski/cve_store_service/batch/IngestionJob.java`)
- [x] Persistence via JPA entities + Flyway migrations (see `cve-store-service/src/main/resources/db/migration`)
- [x] Indexes tuned for CVE lookups and enrichment queries (see `cve-store-service/src/main/resources/db/migration`)
- [x] REST endpoints for CVE/EPSS lookups (see `cve-store-service/src/main/java/com/finki/vladislavangelovski/cve_store_service/api/CveEntryController.java`)
- [x] Optional admin endpoints for ingestion control (trigger ingestion now API added)

## Phase 4 – scan-service
- [x] Integrate Trivy and parse results into a normalized scan DTO (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/core/impl/JacksonTrivyParser.java`)
- [x] Business logic attaches CVE IDs + affected package info in findings (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/dto/Finding.java`)
- [x] REST API: submit image → normalized results; retrieve by `scanId` with optional raw output (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/ScanController.java`)
- [x] Preserve raw scan output (persisted; `raw=true` retrieval) (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/ScanController.java`)
- [x] Redis caching (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/core/impl/RedisScanCache.java`)
- [x] Job coordination via Redis (async job/status model with Redis-backed store)

## Phase 5 – ai-service (RAG)
- [x] Embedding generation + vector store (pgvector) (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/indexing/EmbeddingIndexService.java`)
- [x] Ingest CVE descriptions + EPSS into embeddings (indexing exists; can be triggered via admin endpoint, and image-based QA/claim auto-indexes scan CVEs on demand) (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/EmbeddingsAdminController.java`)
- [x] QA pipeline: retrieval → prompt → LLM → answer + evidence (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/qa`)
- [x] Endpoints exposed: `/qa/claim` and `/qa/question` (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/QaController.java`)
- [x] Admin endpoints for indexing + semantic search (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/EmbeddingsAdminController.java`)
- [x] Deterministic prompt templates stored in resources
- [x] “Evidence always returned” guarantee via CVE-store fallback when embeddings are sparse

## Phase 6 – gateway-service
- [x] Spring Cloud Gateway routing to downstream services (see `gateway-service/src/main/resources/application.yml`)
- [x] Authentication/authorization at the gateway (configurable API key filter)
- [x] Request logging (see `gateway-service/src/main/java/com/finki/vladislavangelovski/gateway_service/filter/RequestLoggingFilter.java`)
- [ ] Correlation IDs propagated end-to-end
- [x] Global error handling and consistent error payloads across services (gateway normalizes errors to common payload)
- [x] Aggregate/normalize responses where needed (gateway CVE aggregate endpoint)
- [x] Swagger/OpenAPI exposure as single entrypoint (gateway aggregates service docs) (see `gateway-service/src/main/resources/application.yml`)

## Phase 7 – Frontend Web Application (UI)
- [x] Choose stack: React + TypeScript + Vite
- [x] UI library: MUI
- [x] API client: typed wrapper (see `frontend/src/api/client.ts` and `frontend/src/api/types.ts`)
- [x] Config: `VITE_API_BASE_URL`
- [x] Core screens (MVP): Dashboard, Image Risk Assessment, QA, CVE Lookup
- [x] Optional: Scan Viewer, Admin Embeddings (admin mode)
- [x] Dockerfile for frontend
- [x] Add frontend to `docker-compose.yml`
- [ ] Add frontend route in Kubernetes phase

## Phase 8 – Local Dev & Orchestration
- [x] Dockerfiles for each backend module (see `*/Dockerfile`)
- [x] Dockerfile for frontend (see `frontend/Dockerfile`)
- [x] `docker-compose.yml` includes gateway, scan, cve-store, ai, Redis, PostgreSQL (see `docker-compose.yml`)
- [x] `docker-compose.yml` includes frontend (see `docker-compose.yml`)
- [x] Validate end-to-end locally via UI + gateway
- [x] Seed/dev data strategy (optional; beyond live NVD/EPSS pulls)

## Phase 9 – CI/CD & Quality
- [x] GitHub Actions: build → test (`mvn ... verify`) (see `.github/workflows/ci.yml`)
- [x] GitHub Actions: Docker build (and push on main/tags) (see `.github/workflows/ci.yml`)
- [x] Formatting/lint gates enforced in CI (Spotless check job)
- [x] OWASP dependency checks (dependency-check Maven plugin + CI job)
- [x] JUnit 5 tests exist
- [x] Spring Boot integration tests exist (H2-backed) (see `cve-store-service/src/test/java/com/finki/vladislavangelovski/cve_store_service/api/CveEntryControllerSpringBootTest.java`)
- [ ] Testcontainers integration tests (Postgres) (not present)
- [ ] Service contract tests (gateway ↔ services)
- [ ] Frontend CI (typecheck + build + UI smoke tests) (frontend exists; CI not added yet)
- [ ] Compose-based smoke test for the full flow (no script/job yet)

## Phase 10 – Kubernetes Deployment
- [ ] Manifests or Helm charts (Deployments, Services, ConfigMaps, Secrets) (none in repo yet)
- [ ] Ingress + TLS (Let’s Encrypt) (none in repo yet)
- [ ] Environment-based config (dev/stage/prod) (none in repo yet)
- [ ] CI deploy step (optional) (none in repo yet)

## Phase 11 – Observability & Monitoring
- [x] Actuator health endpoints exposed across services (see `docker-compose.yml`)
- [ ] Metrics endpoints exposed and scraped (Prometheus/Grafana)
- [ ] Tracing/correlation IDs end-to-end (gateway → services)
- [ ] Centralized logging (ELK/EFK or alternative)

## Phase 12 – Load Testing & Security Validation
- [ ] Performance tests (k6/Gatling) for assess + QA + lookup
- [ ] Security scans: dependency + container + basic DAST (ZAP)
- [ ] Mini pen-test rounds (auth bypass, rate limit, input validation)
- [ ] Document results and mitigations

## Phase 13 – Documentation & Thesis Writing
- [x] README exists (see `README.md`)
- [x] Swagger/OpenAPI exists (services + gateway aggregation)
- [ ] Example requests / runnable API collection (partial: `scan-service/scans.http`)
- [ ] Comprehensive README with architecture diagrams and quickstart
- [ ] “How it works” docs (scan → enrich → score → QA evidence flow)
- [ ] Thesis chapters tracked (not present in this repo yet)

## Phase 14 – Public Deployment & Handoff
- [ ] Deploy to a cloud/on-prem Kubernetes cluster
- [ ] Configure DNS & TLS, finalize performance baselines
- [ ] Handoff guide + maintenance notes (runbooks, upgrades, key rotation, backups)

---

## Critical-path demo checklist (gateway-only)
- [x] Start stack with `docker compose up --build` (see `docker-compose.yml`)
- [x] Ensure CVE/EPSS data exists (startup ingest enabled; EPSS mode auto-seeds NVD when DB is empty)
- [x] Assess an image via gateway: `POST /api/v1/assess/image`
- [x] Ask a QA question via gateway: `POST /api/v1/qa/question` (image-based QA auto-indexes scan CVEs)
- [x] (Optional) Pre-index embeddings via gateway: `POST /api/v1/admin/embeddings/index` (improves semantic-only queries)
