# End-to-end plan (roadmap-mapped checklist)

This file mirrors `ROADMAP.md` and tracks implementation status based on what exists in this repository today (code +
config). Keep it updated as phases are completed.

Legend:
- `- [x]` done in repo
- `- [ ]` missing / needs work (or only partially done)

## Phase 0 – Define & Align Requirements & Scope
- [ ] Revisit functional requirements: scan images, ingest CVE/EPSS, RAG QA (beyond `ROADMAP.md`/`README.md`)
- [ ] Revisit non-functional requirements: performance, scalability, security, reliability, cost (beyond `ROADMAP.md`)
- [ ] Draft minimal API spec (gateway-first) as a stable external contract
  - [x] claim/question in → summary + evidence out (endpoints exist: `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/QaController.java`)
  - [x] assess image in → risk summary + findings out (endpoint exists: `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/AssessmentController.java`)
- [x] Tech-stack confirmation: Java 21 + Spring Boot 3.x (see `pom.xml`)
- [x] Maven multi-module layout (modules exist: `common`, `cve-store-service`, `scan-service`, `ai-service`, `gateway-service`) (see `pom.xml`)
- [x] Redis + PostgreSQL wired (see `docker-compose.yml`)
- [x] Docker Compose (local) (see `docker-compose.yml`)
- [ ] Kubernetes (production target) tracked in repo (no manifests/helm yet)
- [x] Spring Boot Actuator enabled (health endpoints + compose healthchecks) (see `docker-compose.yml`)
- [ ] Micrometer metrics export configured (no Prometheus registry/config yet; metrics endpoints not exposed)
- [x] GitHub Actions CI/CD present (see `.github/workflows/ci.yml`)

## Phase 1 – Repository & Project Skeleton
- [x] Git repository exists (see `.git/`)
- [ ] Branch strategy documented (no `CONTRIBUTING.md` / documented workflow yet)
- [x] Parent POM with shared properties (Java 21) and modules (see `pom.xml`)
- [x] GitHub Actions workflow runs Maven build/verify on PRs (see `.github/workflows/ci.yml`)

## Phase 2 – “common” Module
- [x] Shared DTOs/models exist (see `common/src/main/java/com/finki/vladislavangelovski/common/dto`)
- [ ] Common utilities: HTTP clients, exception wrappers, shared error model, validation helpers (not centralized in `common` yet)
- [ ] Common Jackson configuration and version constants (not centralized in `common` yet)
- [x] DependencyManagement for consistent library versions (see `pom.xml`)

## Phase 3 – cve-store-service
- [x] Ingestion pipeline for NVD CVEs and EPSS CSV (scheduled pulls) (see `cve-store-service/src/main/java/com/finki/vladislavangelovski/cve_store_service/batch/IngestionJob.java`)
- [x] Persistence via JPA entities + Flyway migrations (see `cve-store-service/src/main/resources/db/migration`)
- [x] Indexes tuned for CVE lookups and enrichment queries (see `cve-store-service/src/main/resources/db/migration`)
- [x] REST endpoints for CVE/EPSS lookups (see `cve-store-service/src/main/java/com/finki/vladislavangelovski/cve_store_service/api/CveEntryController.java`)
- [ ] Optional admin endpoints for ingestion control (no explicit “trigger ingestion now” API yet)

## Phase 4 – scan-service
- [x] Integrate Trivy and parse results into a normalized scan DTO (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/core/impl/JacksonTrivyParser.java`)
- [x] Business logic attaches CVE IDs + affected package info in findings (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/dto/Finding.java`)
- [x] REST API: submit image → normalized results; retrieve by `scanId` with optional raw output (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/ScanController.java`)
- [x] Preserve raw scan output (persisted; `raw=true` retrieval) (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/ScanController.java`)
- [x] Redis caching (see `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/core/impl/RedisScanCache.java`)
- [ ] Job coordination via Redis (no async job/status model yet)

## Phase 5 – ai-service (RAG)
- [x] Embedding generation + vector store (pgvector) (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/indexing/EmbeddingIndexService.java`)
- [x] Ingest CVE descriptions + EPSS into embeddings (indexing exists; can be triggered via admin endpoint, and image-based QA/claim auto-indexes scan CVEs on demand) (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/EmbeddingsAdminController.java`)
- [x] QA pipeline: retrieval → prompt → LLM → answer + evidence (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/qa`)
- [x] Endpoints exposed: `/qa/claim` and `/qa/question` (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/QaController.java`)
- [x] Admin endpoints for indexing + semantic search (see `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/api/EmbeddingsAdminController.java`)
- [ ] Deterministic prompt templates stored in resources (prompting is currently code-driven)
- [ ] “Evidence always returned” guarantee (semantic-only responses can be sparse if embeddings are not indexed)

## Phase 6 – gateway-service
- [x] Spring Cloud Gateway routing to downstream services (see `gateway-service/src/main/resources/application.yml`)
- [ ] Authentication/authorization at the gateway (currently disabled)
- [x] Request logging (see `gateway-service/src/main/java/com/finki/vladislavangelovski/gateway_service/filter/RequestLoggingFilter.java`)
- [ ] Correlation IDs propagated end-to-end
- [ ] Global error handling and consistent error payloads across services (gateway mostly passes errors through today)
- [ ] Aggregate/normalize responses where needed (not implemented)
- [x] Swagger/OpenAPI exposure as single entrypoint (gateway aggregates service docs) (see `gateway-service/src/main/resources/application.yml`)

## Phase 7 – Frontend Web Application (UI)
- [x] Choose stack: React + TypeScript + Vite
- [x] UI library: MUI
- [x] API client: generated from OpenAPI or typed wrapper
- [x] Config: `VITE_API_BASE_URL`
- [x] Core screens (MVP): Dashboard, Image Risk Assessment, QA, CVE Lookup
- [ ] Optional: Scan Viewer, Admin Embeddings (admin mode)
- [x] Dockerfile for frontend
- [x] Add frontend to `docker-compose.yml`
- [ ] Add frontend route in Kubernetes phase

## Phase 8 – Local Dev & Orchestration
- [x] Dockerfiles for each backend module (see `*/Dockerfile`)
- [x] Dockerfile for frontend (see `frontend/Dockerfile`)
- [x] `docker-compose.yml` includes gateway, scan, cve-store, ai, Redis, PostgreSQL (see `docker-compose.yml`)
- [x] `docker-compose.yml` includes frontend (see `docker-compose.yml`)
- [ ] Validate end-to-end locally via UI + gateway (UI not present yet)
- [ ] Seed/dev data strategy (optional; beyond live NVD/EPSS pulls)

## Phase 9 – CI/CD & Quality
- [x] GitHub Actions: build → test (`mvn ... verify`) (see `.github/workflows/ci.yml`)
- [x] GitHub Actions: Docker build (and push on main/tags) (see `.github/workflows/ci.yml`)
- [ ] Formatting/lint gates (Spotless/Checkstyle) enforced in CI (Spotless is present but not wired as a gate)
- [ ] OWASP dependency checks (or other SCA tooling)
- [x] JUnit 5 tests exist
- [x] Testcontainers integration tests exist (see `cve-store-service/src/test/java/com/finki/vladislavangelovski/cve_store_service/api/CveEntryControllerIT.java`)
- [ ] Service contract tests (gateway ↔ services)
- [ ] Frontend CI (typecheck + build + UI smoke tests) (frontend not present yet)
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
- [ ] (Optional) Pre-index embeddings via gateway: `POST /api/v1/admin/embeddings/index` (improves semantic-only queries)
