# Docker Image Risk Assessment Application — Project Roadmap (Java 21)

## Phase 0 – Define & Align Requirements & Scope
- Revisit functional requirements: scan images, ingest CVE/EPSS, RAG QA (claim + question)
- Revisit non-functional requirements: performance, scalability, security, reliability, cost
- Draft minimal API spec:
  - claim/question in → summary + evidence out
  - assess image in → risk summary + findings out
- Tech-stack confirmation: **Java 21 + Spring Boot 3.x**, Maven multi-module
- Modules: common, cve-store-service, scan-service, ai-service, gateway-service
- Redis (cache, job coordination), PostgreSQL (relational + vectors if used)
- Docker Compose (local), Kubernetes (prod)
- Spring Boot Actuator + Micrometer
- GitHub Actions CI/CD

## Phase 1 – Repository & Project Skeleton
- Create GitHub repo `docker-image-risk-assessment-application`
- Define branch strategy (main + feature branches)
- Set up parent POM with modules placeholders and shared properties (Java 21)
- Stub GitHub Actions workflow to run Maven build on PRs

## Phase 2 – “common” Module
- Define shared DTOs/models (CVE, EPSS, ImageScanResult, AssessmentRequest/Response, QARequest/Response)
- Common utilities: HTTP clients, exception wrappers, shared error model, validation helpers
- Common Jackson configuration and version constants
- DependencyManagement for consistent library versions across services

## Phase 3 – cve-store-service
- Ingestion pipeline for NVD CVEs and EPSS CSV (scheduled pulls)
- Persistence via JPA entities + Flyway/Liquibase migrations
- Indexes tuned for CVE lookups and enrichment queries
- Expose REST endpoints for CVE/EPSS lookups (and optional admin endpoints for ingestion control)

## Phase 4 – scan-service
- Integrate a scanner (e.g., Trivy) and parse results into normalized `ImageScanResult`
- Business logic to attach CVE IDs and affected packages/layers
- REST API to submit image (name/tag) → raw scan report + normalized results
- Caching and job coordination via Redis (optional but recommended)

## Phase 5 – ai-service (RAG)
- Set up embedding generation + vector store (pgvector / Weaviate / etc.)
- Ingest CVE descriptions + EPSS (and optionally CVSS) into embeddings
- Build QA pipeline: retrieval → prompt → LLM → answer + summarization + evidence
- Expose endpoints for:
  - `/qa/claim`
  - `/qa/question`
- Add admin endpoints for embeddings indexing + semantic search (for demo/debug)

## Phase 6 – gateway-service
- Spring Cloud Gateway routing to downstream services
- Implement API-key security, request logging, correlation IDs
- Global error handling and consistent error payloads
- Aggregate/normalize responses where needed
- Swagger/OpenAPI exposure (single entrypoint)

## Phase 7 – Frontend Web Application (UI)
**Goal:** a demo/thesis-ready UI that uses only the gateway and proves the full workflow end-to-end.

### 7.1 Foundations
- Choose stack: React + TypeScript + Vite
- UI library: MUI (recommended for consistency and speed)
- API client: generated from OpenAPI or a typed fetch wrapper
- Config: `VITE_API_BASE_URL`, API-key handling (session input / sessionStorage)

### 7.2 Core screens (MVP)
- Dashboard: quick assess + quick QA + recent history
- Image Risk Assessment: imageRef → score/band → top findings + evidence + export JSON
- QA: claim/question toggle → answer + evidence + retrieved items (if available)
- CVE Lookup: CVE details + EPSS + references + raw JSON

### 7.3 Optional high-value screens
- Scan Viewer: raw scan JSON + normalized table
- Admin Embeddings: index + semantic search (locked behind “admin mode”)

### 7.4 Packaging & deployment alignment
- Dockerfile for frontend
- Add frontend to docker-compose (local)
- Add frontend Deployment/Service/Ingress route in Kubernetes phase

## Phase 8 – Local Dev & Orchestration
- Write Dockerfiles for each backend module + frontend
- Create/maintain `docker-compose.yml` including gateway, scan, cve-store, ai, frontend, Redis, PostgreSQL
- Validate end-to-end locally via UI + gateway
- Seed/dev data strategy (optional): small CVE subset for fast demo startup

## Phase 9 – CI/CD & Quality
- Enhance GitHub Actions: build → test → Docker build & push
- Add formatting/lint gates (Spotless/Checkstyle)
- Add OWASP dependency checks (and/or other SCA tooling)
- JUnit 5 unit tests + Testcontainers integration tests
- Service contract tests (gateway ↔ services, API response stability)
- Frontend CI: typecheck + build + minimal UI smoke tests

## Phase 10 – Kubernetes Deployment
- Manifests or Helm charts for Deployments, Services, ConfigMaps, Secrets (backend + frontend)
- Configure Ingress (NGINX controller) with TLS (Let’s Encrypt)
- Environment-based config (dev/stage/prod)
- Integrate helm upgrade / apply steps into CI for main merges (if desired)

## Phase 11 – Observability & Monitoring
- Expose Actuator health/metrics across services
- Scrape metrics with Prometheus, visualize with Grafana
- Tracing/correlation IDs end-to-end (gateway → services)
- Optional: centralized logging via ELK/EFK or lightweight alternatives

## Phase 12 – Load Testing & Security Validation
- Performance tests (k6, Gatling) for key endpoints (assess + QA + lookup)
- Security scans: dependency scanning + container scanning + basic DAST (OWASP ZAP)
- Mini pen-test rounds: auth bypass attempts, rate limit tests, input validation tests
- Document results and mitigations (thesis-ready evidence)

## Phase 13 – Documentation & Thesis Writing
- Comprehensive README with architecture diagrams and quickstart
- Swagger/OpenAPI documentation and example requests
- “How it works” docs: scan → enrich → score → QA evidence flow
- Thesis chapters in parallel:
  - Introduction & motivation
  - Related work (RAG, vuln scanning, EPSS)
  - Design & architecture
  - Implementation details
  - Evaluation & results
  - Conclusion & future work

## Phase 14 – Public Deployment & Handoff
- Deploy to a cloud/on-prem Kubernetes cluster
- Configure DNS & TLS, finalize performance baselines
- Handoff guide + maintenance notes (runbooks, upgrades, key rotation, backups)
