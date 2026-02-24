# Docker Image Risk Assessment Application - System Documentation

Generated: 2026-02-24 (based on the repository contents in this workspace).

This document is the "single deep dive" description of the system: architecture, services, data
model, APIs, technology choices, configuration, and the reasons those pieces exist in their
current shape.

Prime directive for accuracy: every statement here is derived from code/config in this repo
(`pom.xml`, `docker-compose.yml`, `*/src/main/**`, `*/src/main/resources/**`, migrations, CI).
Where the system is incomplete or intentionally "MVP", this doc calls that out explicitly.

---

## 1. What the system does

The application assesses the risk of Docker/container images by combining:

1) A vulnerability scan of an image (Trivy) -> normalized findings + raw scan JSON
2) Vulnerability intelligence data:
   - CVE metadata (NVD)
   - Exploit Prediction Scoring System (EPSS)
3) Risk scoring and AI-assisted explanations:
   - Image risk summary (score + band + top findings)
   - RAG-style follow-up question answering with citations

The "contract boundary" is the `gateway-service`. External clients and the `frontend` call the
gateway only.

---

## 2. Top-level architecture

### 2.1 Services and responsibilities

The system is a multi-service application with these major components:

- `gateway-service` (Spring Cloud Gateway, WebFlux)
  - Single public HTTP entrypoint (host port `8080`)
  - Routes `/api/**` to downstream services
  - Optional JWT authN/authZ (Keycloak-backed in `docker-compose.yml`)
  - Request ID generation/propagation
  - User context propagation (X-User-Id / X-User-Name / X-User-Email)
  - Error response normalization (consistent error JSON for upstream failures)
  - Aggregated OpenAPI/Swagger UI (points to each service's `/v3/api-docs`)

- `scan-service` (Spring MVC)
  - Runs Trivy scans (`trivy image --format json ...`)
  - Produces:
    - Raw Trivy JSON (stored)
    - Normalized scan model (`ScanResult`) (returned and stored)
  - Caches scan results in Redis (if Redis available) or in-memory fallback
  - Persists scan summary + findings + raw JSON in PostgreSQL (schema `scan`)
  - Provides sync and async scan endpoints

- `cve-store-service` (Spring MVC, JPA)
  - Ingests CVE data from NVD's CVE 2.0 API (scheduled and on-demand)
  - Ingests EPSS scores from the public gzipped "current" CSV feed
  - Stores CVE entries and latest EPSS scores in PostgreSQL (public schema)
  - Provides read APIs for CVEs and EPSS
  - Provides admin endpoints to trigger ingestion

- `ai-service` (Spring MVC + WebClient/WebFlux for downstream calls)
  - "Assess image" workflow:
    - Calls `scan-service` to obtain scan findings (cached or new scan)
    - Calls `cve-store-service` to enrich findings with CVE + EPSS
    - Computes an overall risk score and risk band
  - RAG QA workflow:
    - Retrieves relevant CVEs via vector search (pgvector)
    - Calls an LLM via Spring AI (Ollama) using prompt templates stored in repo
    - Returns answer with citations
    - Optionally restricts QA to CVEs present in a scanned image
  - Embeddings indexing:
    - Fetches CVEs from `cve-store-service`
    - Creates embeddings via Ollama
    - Stores embeddings in Postgres table `cve_embeddings` (pgvector)

- `common` (library jar)
  - Shared DTOs used across services (e.g. assessment + QA request/response types)
  - Shared error model (`common.error.ErrorResponse`)
  - Common Jackson configuration (`CommonObjectMapperFactory`)
  - Small utilities (validation, HTTP client factory)

- `frontend` (React + Vite, served by Nginx in compose)
  - Web UI for scans, assessments, CVE lookup, QA and embeddings admin
  - Uses Keycloak (OIDC) for authentication in-browser
  - Calls the gateway only (`VITE_API_BASE_URL`, runtime `config.js`)

Supporting runtime dependencies (Docker Compose):

- PostgreSQL (`pgvector/pgvector:pg17`): primary datastore, also used for vector embeddings
- Redis (`redis:7`): cache + async job store (optional; scan-service falls back to in-memory)
- Keycloak (`quay.io/keycloak/keycloak:25.0.6`): auth provider (realm imported at startup)
- Keycloak DB (Postgres): separate Postgres instance for Keycloak
- Ollama (`ollama/ollama:latest`): local model runtime for embeddings + chat

### 2.2 Network topology (Docker Compose)

In `docker-compose.yml`, only the gateway and frontend are published to the host:

- `gateway-service`: `localhost:8080` -> container `8080`
- `frontend`: `localhost:5173` -> container `80`

Other services are internal-only on the compose network:

- `scan-service`: container `8080`
- `cve-store`: container `8080`
- `ai-service`: container `8083`
- `keycloak`: container `8080` (reachable publicly through gateway `/auth/**`)
- `postgres`: `5432` (also published to host for local inspection)
- `redis`: `6379` (published to host)
- `ollama`: `11434` (published to host)

This layout enforces the intended contract boundary: clients talk to the gateway; services talk
to each other on the internal network.

### 2.3 End-to-end request flows (high level)

Image assessment (`frontend` or any client -> `gateway`):

1) `POST /api/v1/assess/image` (gateway)
2) gateway rewrites to `ai-service` `POST /api/assess/image`
3) `ai-service` calls `scan-service`:
   - tries `GET /api/v1/scans?imageRef=...` to reuse an existing scan
   - falls back to `POST /api/v1/scans` to run a new Trivy scan
4) `scan-service`:
   - runs Trivy
   - parses raw JSON -> normalized findings
   - persists normalized + raw JSON in Postgres, caches in Redis
5) `ai-service` enriches findings via `cve-store-service`:
   - `GET /api/v1/cves/{cveId}` (+ optional `GET /api/v1/cves/{cveId}/epss`)
6) `ai-service` computes overall score + risk band + top findings
7) response returns to client via gateway

QA question (`frontend` or any client -> `gateway`):

1) `POST /api/v1/qa/question` (gateway)
2) gateway rewrites to `ai-service` `/api/qa/question`
3) `ai-service`:
   - if `imageRef` present, obtains scan findings and restricts QA to those CVEs
   - auto-indexes missing embeddings for those CVEs (best-effort)
   - performs vector search over `cve_embeddings`
   - calls chat model via Spring AI (Ollama) with prompt templates + evidence context
   - appends user/assistant turns to server-side conversation history
4) response returns to client via gateway (citations + `conversationId`)

QA history (assessment follow-up chat):
- `GET /api/v1/qa/history?chatScopeId=...&limit=...` returns saved conversations + turns for the current user and scope
- `DELETE /api/v1/qa/history/{conversationId}` deletes one saved conversation (messages cascade)

---

## 3. Technology stack (what is used, where, and why)

### 3.1 Language + runtime

- Java 21 across backend services (see root `pom.xml` and module POMs)
  - Modern language/runtime features
  - Spring Boot 3.x requires a recent Java baseline; repo pins Boot `3.5.4`

- Node.js 20 (for frontend build and CI), Vite + TypeScript + React 18

### 3.2 Frameworks and major libraries

Backend:

- Spring Boot `3.5.4` (dependency management via `spring-boot-dependencies`)
- Spring Cloud `2024.0.0` in `gateway-service` (Spring Cloud Gateway)
- Spring Security (WebFlux security in gateway):
  - OAuth2 resource server / JWT support
- Spring AI BOM `1.1.1` and `spring-ai-starter-model-ollama` in `ai-service`
  - Chat: prompts and structured responses
  - Embeddings: used indirectly via custom Ollama embedding client and/or Spring AI chat
- Springdoc OpenAPI (`springdoc-openapi-starter-*`) in services
  - Gateway publishes an aggregated Swagger UI that points to each service's docs

Persistence:

- PostgreSQL (runtime DB)
- Flyway (schema migrations; used by `cve-store-service` and `scan-service`)
- Spring Data JPA in `cve-store-service`
- Spring JDBC/JdbcTemplate and NamedParameterJdbcTemplate in `scan-service` and `ai-service`

Caching + job coordination:

- Redis (`spring-boot-starter-data-redis`) in `scan-service`
  - Used for scan caching and async job status when available
  - In-memory fallbacks exist for both cache and job store when Redis is absent

Scanning:

- Trivy v0.67.2 installed into the `scan-service` Docker image
  - Invoked as an external process from Java using `ProcessBuilder`

Auth:

- Keycloak 25.0.6
  - Realm `risk` imported on startup via `keycloak/realm-risk.json`
  - Frontend is configured as OIDC public client `risk-console`

AI / RAG:

- Ollama
  - Embeddings endpoint `/api/embed` (preferred) with fallback to `/api/embeddings`
  - Chat model driven by Spring AI configuration (`spring.ai.ollama.chat.options.model`)
- pgvector extension in Postgres
  - Table `cve_embeddings` stores embeddings in a `vector(768)` column
  - Index created using `hnsw (embedding vector_cosine_ops)` in migration `V5__enrich_cve_embeddings.sql`

Frontend:

- React + React Router
- MUI + Emotion for UI components/styling
- Keycloak JS for auth
- Nginx serves the SPA build and exposes a runtime config file (`/config.js`)

### 3.3 Why these choices (as reflected by implementation)

The design favors:

- A stable edge contract and centralized security: `gateway-service` as the only public backend.
- Keeping local development self-contained:
  - Ollama instead of a hosted LLM provider by default
  - Postgres + pgvector instead of a separate vector DB service
- Auditability and reproducibility:
  - Storing raw scan JSON (`scan.scan_raw`) alongside normalized results
  - Returning citations with QA/assessment outputs
- Resilience to partial dependencies:
  - scan-service uses Redis when present, otherwise in-memory implementations
  - ai-service has best-effort fallbacks if vector search or LLM calls fail
  - cve-store ingestion is designed not to prevent the service from starting

---

## 4. Build, packaging, and CI/CD

### 4.1 Maven multi-module build

The root `pom.xml` defines these Maven modules:

- `common`
- `cve-store-service`
- `scan-service`
- `ai-service`
- `gateway-service`

Notable build tooling:

- Spotless (Google Java Format) is configured in plugin management and enforced in CI.
- OWASP dependency-check plugin is configured (only executed in CI when an `NVD_API_KEY` secret is set).
- Surefire plugin is pinned.

### 4.2 Docker images

Each backend module has a Dockerfile that:

- Builds the module using Maven in a build stage
- Copies the resulting fat jar into an Eclipse Temurin JRE image

Special-case Docker images:

- `scan-service` image installs Trivy via Aquasecurity's installer script and pins a version (`v0.67.2`).
- `frontend` builds a static SPA via Vite then serves it from Nginx.

### 4.3 GitHub Actions CI

`.github/workflows/ci.yml` runs:

- Spotless format check
- (Optional) OWASP dependency-check if `NVD_API_KEY` is available
- Frontend build/typecheck via Node 20
- Per-service Maven builds and Docker image builds
- A docker-compose smoke test that starts the full stack and checks gateway endpoints

---

## 5. Runtime configuration and environment variables

### 5.1 `.env` strategy

- The repo includes `.env-example` and ignores `.env` in `.gitignore`.
- Docker Compose loads `.env` via `env_file` for Postgres and cve-store.
- `cve-store-service` also imports the root `.env` when run locally outside compose via:
  `spring.config.import: optional:file:../.env[.properties]`

### 5.2 Common runtime knobs (compose)

From `docker-compose.yml`:

- Database:
  - `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`

- Gateway:
  - `SERVICES_*_BASE_URL` to tell the gateway where downstream services live
  - `GATEWAY_SECURITY_ENABLED` to enable gateway security
  - JWT config:
    - `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`
    - `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`

- cve-store ingestion:
  - `INGEST_ENABLED`
  - `INGEST_STARTUP_ENABLED`, `INGEST_STARTUP_MODE` (EPSS vs NVD vs ALL)
  - `INGEST_NVD_LOOKBACK_DAYS`, `INGEST_NVD_MAX_WINDOW_DAYS`
  - `INGEST_BOOTSTRAP_ENABLED`
  - `NVD_API_KEY` is supported (read by cve-store), but is not set in compose by default

- Scan service:
  - `TRIVY_CACHE_DIR`
  - `SCAN_DEFAULTS_TIMEOUT_SEC` (compose overrides Trivy default scan timeout to 300s)
  - `/var/run/docker.sock` is mounted to allow scanning local Docker images that are not pullable from a registry

- AI service:
  - downstream service URLs/paths under `SERVICES_*`
  - HTTP timeouts under `SERVICES_HTTP_*_TIMEOUT_MS` (overrides defaults)
  - embeddings:
    - `EMBEDDINGS_BASE_URL`, `EMBEDDINGS_MODEL`, `EMBEDDINGS_EXPECTED_DIM`
    - startup indexing controls `EMBEDDINGS_STARTUP_*`
  - chat model:
    - `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL`

- Frontend:
  - `VITE_API_BASE_URL` build arg (and runtime config via `config.js`)

### 5.3 Why the config looks like this

Patterns you see in this repo are deliberate:

- Most service-to-service addresses are configured as base URLs + paths.
  - This avoids hardcoding service discovery and makes compose/k8s wiring explicit.
- Gateway has a "secure-by-default" posture in config:
  - CORS allowlist is empty by default in `application.yml` and opened in `application-dev.yml`.
  - Trusted proxies are restrictive by default and opened in `dev`.
- AI service timeouts are intentionally overridable because:
  - scanning and LLM calls can take longer than typical HTTP service calls.

---

## 6. Data model (PostgreSQL)

PostgreSQL is used both for relational data and for vector embeddings (pgvector).

### 6.1 CVE and EPSS tables (public schema)

Defined by cve-store Flyway migrations (`cve-store-service/src/main/resources/db/migration/*`):

- `cve_entry`
  - `cve_id` (PK)
  - `description` (TEXT; nullable after `V2__cve_columns_nullable.sql`)
  - `published_date` (TIMESTAMP)
  - `modified_date` (TIMESTAMP)
  - `cvss_v3_score` (NUMERIC(3,1))

- `epss_score`
  - `id` (BIGSERIAL PK)
  - `cve_id` (FK -> `cve_entry.cve_id` with cascade delete)
  - `score` (NUMERIC(5,4))
  - `percentile` (NUMERIC(5,2))
  - `retrieved_at` (TIMESTAMP NOT NULL)
  - uniqueness is enforced as "latest-only" via:
    - `V4__remove_retrieved_at_from_epss_score_constraint.sql` -> UNIQUE(cve_id)
  - upsert behavior is implemented in `EpssScoreRepository.upsert(...)` using `ON CONFLICT (cve_id)`

### 6.2 Scan tables (schema `scan`)

Defined by scan-service Flyway migration `scan-service/src/main/resources/db/migration/V001__create_scan_schema.sql`:

- `scan.scans` (one row per scan ID)
  - scan identity: `scan_id` (UUID PK), `image`, `digest`, `scanner_version`
  - timestamps: `started_at`, `finished_at`
  - rollups: total, critical/high/medium/low/unknown counts, fix_available count

- `scan.scan_findings` (normalized findings)
  - FK `scan_id` -> `scan.scans`
  - vulnerability fields: `cve_id`, package, installed/fixed versions, severity, CVSS, references
  - `ref_urls` stored as JSONB (GIN index exists)

- `scan.scan_raw` (raw Trivy JSON)
  - `scan_id` PK/FK to `scan.scans`
  - `raw_json` JSONB

This split supports:

- efficient listing/aggregation (summary/findings) without parsing large JSON
- full audit/debug capability via raw JSON retention

### 6.3 Vector embeddings table (public schema)

Defined and/or ensured by migration `cve-store-service/src/main/resources/db/migration/V5__enrich_cve_embeddings.sql`:

- `cve_embeddings`
  - `cve_id` (NOT NULL)
  - `chunk_no` (NOT NULL; MVP uses 0 always)
  - `title`, `description`, `chunk_text`
  - `cvss_base`, `epss`, `epss_percentile`
  - `cwe` (comma-separated string in current writer)
  - `published`, `last_modified` (TIMESTAMPTZ)
  - `embed_model`, `embed_version`
  - `embedding vector(768)` (NOT NULL)
  - `updated_at` (NOT NULL)
  - constraint: UNIQUE(cve_id, chunk_no)
  - index: `hnsw (embedding vector_cosine_ops)` for approximate nearest neighbor search

This allows fast semantic retrieval of relevant CVEs for QA.

### 6.4 QA conversation history tables

Defined by `cve-store-service` migration `V7__qa_conversation_history.sql`:

- `qa_chat_conversations`
  - identity/scope: `conversation_id` (UUID PK), `user_id`, optional `user_name`, `chat_scope_id`
  - context: optional `image_ref`, `title`
  - timestamps: `created_at`, `updated_at`
  - indexes:
    - `(user_id, chat_scope_id, updated_at DESC)` for assessment-scope history loading
    - `(user_id, updated_at DESC)` for recency listing

- `qa_chat_messages`
  - `message_id` (PK), FK `conversation_id` -> `qa_chat_conversations` (cascade delete)
  - message fields: `role` (`user` or `assistant`), `content`, optional `citations_json`
  - timestamp: `created_at`
  - index: `(conversation_id, created_at ASC, message_id ASC)`

Legacy table note:
- `qa_chat_history` from `V6__qa_chat_history.sql` remains in older environments but is not used by the current follow-up chat flow.

---

## 7. API surface (gateway-first)

### 7.1 Public entrypoint

Gateway is the only public backend for clients:

- Base URL: `http://localhost:8080` (docker-compose)
- Swagger UI: `GET /swagger-ui.html` (gateway aggregates service docs)
- Health:
  - `GET /actuator/health` (Spring Boot actuator)
  - `GET /health` (custom health endpoint with dependency breakdown)

### 7.2 Routed APIs (stable external paths)

From `gateway-service/src/main/resources/application.yml`, the gateway forwards:

Scan:
- `/api/v1/scans/**` -> `scan-service` (no path rewrite)

CVE store:
- `/api/v1/cves/**` -> `cve-store-service`
- `/api/v1/admin/ingestion/**` -> `cve-store-service` (ingestion admin)

AI:
- `/api/v1/assess/**` -> `ai-service` with rewrite `/api/v1/assess...` -> `/api/assess...`
- `/api/v1/qa/**` -> `ai-service` with rewrite `/api/v1/qa...` -> `/api/qa...`
- `/api/v1/admin/embeddings/**` -> `ai-service` with rewrite to `/api/admin/embeddings...`
- `/api/v1/semantic/**` -> `ai-service` with rewrite to `/api/semantic...`

Aliases are also routed without the `/v1` prefix (e.g., `/api/qa/**`).

Auth (Keycloak):
- `/auth/**` -> Keycloak (the gateway exposes Keycloak as part of the "single entrypoint" design)

Gateway-local aggregation endpoint:
- `GET /api/v1/aggregate/cves/{id}` -> gateway does two calls to cve-store:
  - CVE details and EPSS list (limit=1), returns combined payload

### 7.3 Downstream service endpoints (as implemented)

scan-service (`/api/v1/scans`):
- `POST /api/v1/scans` - synchronous scan, returns normalized `ScanResult`
- `POST /api/v1/scans/async` - schedules async scan, returns `ScanJobStatus` (202)
- `GET /api/v1/scans/{scanId}?raw=false|true`
  - `raw=true` returns raw JSON stored by scan-service
- `GET /api/v1/scans/jobs/{scanId}` - async job status
- `GET /api/v1/scans?imageRef=...&raw=false|true` - latest scan for imageRef (DB lookup)

cve-store-service (`/api/v1/cves`):
- `GET /api/v1/cves/{id}` - returns `CveEntryDto` (adds latest EPSS into flattened fields)
- `GET /api/v1/cves?page=...&size=...` - paged list (does not currently join EPSS in the list)
- `GET /api/v1/cves/{id}/epss?limit=...` - returns newest EPSS score; `limit` is clamped to 1
- `PUT /api/v1/cves/{id}` - upserts CVE + latest EPSS in one request payload

cve-store admin ingestion (`/api/v1/admin/ingestion`):
- `POST /api/v1/admin/ingestion/nvd` - trigger NVD ingest (async)
- `POST /api/v1/admin/ingestion/epss` - trigger EPSS ingest (async)
- `POST /api/v1/admin/ingestion/all` - trigger both (async)
- `POST /api/v1/admin/ingestion/bootstrap?epss=true|false` - full historical bootstrap (async)

ai-service:
- Assessment:
  - `POST /api/assess/image` - returns `AssessImageResponse`
  - `GET /api/assess/ping` - returns "ok" (simple check)
- QA:
  - `POST /api/qa/question` - returns `QaQuestionResponse`
  - `GET /api/qa/history?chatScopeId=...&limit=...` - returns conversation history for current user + scope
  - `DELETE /api/qa/history/{conversationId}` - deletes a saved conversation for current user
- Embeddings admin:
  - `POST /api/admin/embeddings/index` - index next batch or explicit `cveIds`
  - `GET /api/admin/embeddings/search?q=...&k=...` - embeds query and searches vector store
- Semantic:
  - `GET /api/semantic/search?q=...&k=...` (convenience)
  - `POST /api/semantic/search` (typed request)
  - `POST /api/semantic/qa` (semantic QA endpoint)

Note: externally, clients should use the gateway paths with `/api/v1/...` (or the gateway aliases).

---

## 8. Deep dive: gateway-service

### 8.1 Routing

Routing is primarily declared in `gateway-service/src/main/resources/application.yml` under
`spring.cloud.gateway.routes`.

Key features:

- Path-based routing (predicates)
- Path rewrites for `ai-service` so external versioned paths map to internal unversioned paths
- OpenAPI doc routing:
  - `/v3/api-docs/ai` -> `ai-service /v3/api-docs`
  - `/v3/api-docs/scan` -> `scan-service /v3/api-docs`
  - `/v3/api-docs/cve-store` -> `cve-store-service /v3/api-docs`
- Keycloak routing to make `/auth/**` part of the single public entrypoint

There is also an optional Java-based `RouteLocator` (`GatewayRoutesConfig`), but it is only enabled
when `gateway.routes.source=java`.

### 8.2 Request IDs and user context

Gateway injects/propagates:

- `X-Request-Id`:
  - Created if missing (`gateway_service/filter/RequestIdFilter.java`)
  - Added to the proxied request and response headers

- User identity headers:
  - `X-User-Id`, `X-User-Name`, `X-User-Email`
  - Derived from the authenticated principal/JWT when security is enabled
  - Added/cleared on every request (`UserContextFilter`)

These headers are available for downstream identity-aware behavior.

### 8.3 Error normalization

There are two layers:

1) `GatewayErrorResponseFilter` (GlobalFilter) wraps *upstream* error responses and rewrites the
   response body into `common.error.ErrorResponse` while preserving the HTTP status.
   - It attempts to extract a message from upstream payload keys like `message`, `detail`, `error`,
     or `title` and uses a fallback if needed.
   - `/auth/**` is excluded so Keycloak can render its own UI and error pages.

2) `GatewayGlobalConfig.errorHandlingFilter` catches unhandled exceptions in the gateway filter
   chain and returns a `503` with a consistent error payload.

Why this exists:

- Downstream services use different error styles (e.g., scan-service custom JSON, other services use
  RFC7807 `ProblemDetail`).
- The gateway makes client error handling simpler by ensuring a consistent envelope on failures.

### 8.4 Security (optional)

`GatewaySecurityConfig` applies when `gateway.security.enabled=true`:

- Permits:
  - `OPTIONS /**` (CORS preflight)
  - `/auth/**` (Keycloak)
  - health endpoints and Swagger UI/docs
- Requires authentication:
  - `/api/**`
- Requires admin role for admin endpoints:
  - `/api/v1/admin/**`, `/api/admin/**` requires `ROLE_ADMIN` or `SCOPE_admin`

Role extraction:

- Reads roles from JWT claims:
  - `roles`
  - `realm_access.roles`
- Normalizes to `ROLE_<UPPER>` authorities

JWT validation:

- When enabled, `GatewayJwtDecoderConfig` creates a `ReactiveJwtDecoder` based on the configured
  `jwk-set-uri` and optional issuer validation.

### 8.5 CORS and trusted proxies

In `application.yml`:

- CORS is deny-all by default (allowedOrigins: `[]`) for `/api/**`
- Trusted proxies allowlist is localhost-only

In `application-dev.yml`:

- CORS is opened (allowedOrigins: `"*"`)
- Trusted proxies are opened (`".*"`) for compose/dev convenience

Why it works this way:

- Production should not allow arbitrary browser origins by default.
- Proxy headers are security-sensitive; trusting all proxies is dangerous outside dev.

---

## 9. Deep dive: scan-service

### 9.1 Scan execution model

Scan execution is orchestrated by `DefaultScanOrchestrator`:

1) Build a `TrivyInvocationRequest`:
   - image ref (e.g., `nginx:1.25`)
   - ignore unfixed (default true)
   - timeout (default 120s)
   - scanners list (MVP restricts to `["vuln"]`)
   - optional registry credentials (username/password)

2) Run Trivy via `ProcessTrivyInvoker`:
   - Constructs a command like:
     `trivy image --format json --scanners vuln --ignore-unfixed --timeout 120s <image>`
   - Sets `TRIVY_USERNAME` / `TRIVY_PASSWORD` env vars if registry creds provided
   - Bounds stdout and stderr size to prevent pathological memory usage
   - Enforces timeout and kills the process on overrun
   - Sanitizes stderr for secrets before error messages are returned
   - Detects Trivy version once (cached) by calling `trivy --version`

3) Parse raw JSON to normalized findings via `JacksonTrivyParser`:
   - Extracts:
     - `ArtifactName` (image name)
     - digests (`RepoDigests[0]`, or `Metadata.ImageID/ArtifactID` fallback)
     - findings from `Results[].Vulnerabilities[]`
   - CVSS selection:
     - prefer "nvd" if present
     - else pick vendor entry with highest V3Score (fallback V2Score)
   - References normalized to URLs (adds scheme if missing)

4) Persist and cache:
   - Saves summary, findings, and raw JSON to Postgres (`JdbcScanPersistence`)
   - Writes to cache (`ScanCache`) with TTL (default 24h)

### 9.2 Cache and job store strategy

`ScanBeans` selects implementations based on whether a Redis template bean exists:

- With Redis:
  - `RedisScanCache` for scan results
  - `RedisScanJobStore` for async job statuses
- Without Redis:
  - `InMemoryScanCache`
  - `InMemoryScanJobStore`

This design keeps local unit/integration testing simple and still allows a production-like cache
when Redis is deployed.

### 9.3 Persistence strategy

Scan persistence uses the `scan` schema and a normalized relational model:

- `scan.scans` stores "header" metadata and rollups for quick listing and retrieval
- `scan.scan_findings` stores the normalized findings
- `scan.scan_raw` stores raw JSON for audit/debug

On retrieval:

- `GET /api/v1/scans/{scanId}` checks Redis first, then falls back to DB.
- On DB fallback, it writes the loaded scan back into the cache (best-effort).

### 9.4 Async scanning

`POST /api/v1/scans/async`:

- creates a job entry in the job store (Redis or in-memory)
- executes a background task via Spring `TaskExecutor`
- provides `GET /api/v1/scans/jobs/{scanId}` to query status

### 9.5 Error handling and safety

scan-service has:

- Input validation in controller (rejects blank/whitespace image refs and unsafe leading `-`)
- A `@ControllerAdvice` that maps known failures to structured error JSON
  - timeouts, parser errors, caching failures, etc.
- Request ID filter (Servlet filter) that:
  - sets MDC `requestId` and returns `X-Request-Id` in response

Why these choices:

- Scanning is a high-risk operation (external process, untrusted image refs). The input guardrails
  reduce trivial injection or malformed invocations.
- Boundaries on output size and timeouts prevent resource exhaustion.

---

## 10. Deep dive: cve-store-service

### 10.1 Data ingestion: NVD (CVE)

`IngestionJob` ingests CVEs from NVD's CVE 2.0 API:

- Scheduled daily at `02:15 UTC`:
  - `@Scheduled(cron = "0 15 2 * * *", zone = "UTC")`
- Uses last-modified window parameters:
  - `lastModStartDate`, `lastModEndDate`
- Pagination:
  - `resultsPerPage=2000` and `startIndex` increments
- Rate limiting strategy:
  - Without `NVD_API_KEY`, it uses a much larger base delay to avoid 429/Cloudflare throttling.
  - On 429, it respects `Retry-After` when present; otherwise exponential backoff.
  - On transient transport errors, exponential backoff is used.
- NVD API constraint:
  - overall ingestion window is chunked into <= 120-day windows to avoid API rejection
  - configured via `ingest.nvd.max-window-days` (clamped to 120)

Mapping:

- Only a subset of NVD data is currently persisted:
  - CVE ID
  - English description
  - published date
  - last modified date
  - first CVSS v3.1 base score found (if available)

This is implemented in `NvdMapper` and `CveEntryEntity`.

### 10.2 Data ingestion: EPSS

EPSS ingestion downloads and parses:

- URL: `https://epss.cyentia.com/epss_scores-current.csv.gz`
- Parses gzipped CSV using OpenCSV, batching writes in groups of 500
- "Latest-only" strategy:
  - Upserts into `epss_score` on conflict by `cve_id` so each CVE has only one current score row.
  - Skips EPSS rows for CVEs not present in `cve_entry`.

### 10.3 Startup ingestion modes

`IngestionJob` also supports a "run once on startup" mode:

- `ingest.startup.enabled=true` triggers startup ingestion
- `ingest.startup.mode`:
  - `EPSS`: run EPSS only, but will run NVD first if the CVE store is empty
  - `NVD`: run NVD only
  - `ALL`: run both

Additionally, there is an optional `NvdBootstrapRunner` that performs a full historical bootstrap
when `ingest.bootstrap.enabled=true` and the CVE table is empty.

Why ingestion is built this way:

- NVD and EPSS ingestion can be slow and should not block service startup.
- Running EPSS without CVEs is mostly wasted work (it will skip most rows), hence the CVE-empty
  safeguard.
- Daily scheduling keeps data fresh without requiring manual intervention.

### 10.4 APIs

`CveEntryController` provides:

- lookup by ID
- paged list
- latest EPSS (clamped to 1)
- upsert endpoint for a combined CVE + EPSS payload

There is also an admin controller (conditionally enabled by `ingest.enabled`) to trigger ingestion.

### 10.5 Error handling style

cve-store-service uses Spring's `ProblemDetail` (RFC7807-ish) for basic exceptions:

- 404 for not found
- 400 for invalid input

The gateway normalizes these to the shared `ErrorResponse` format for clients.

---

## 11. Deep dive: ai-service

ai-service is where "risk assessment" and "RAG QA" are composed.

### 11.1 Downstream service clients

ai-service calls:

- scan-service via `ScanClientImpl`:
  - tries `GET /api/v1/scans?imageRef=...` first (reuse latest scan)
  - submits a new scan via `POST /api/v1/scans` if not found

- cve-store-service via `CveStoreClientImpl`:
  - fetches CVE by ID and optionally latest EPSS
  - supports `getByIds` (implemented as a loop of `getById` calls)
  - supports paging CVE list for embeddings indexing

HTTP client implementation:

- Uses Spring `WebClient` with:
  - configurable connect/response/read/write timeouts
  - retry with exponential backoff for retryable failures
  - request ID propagation from MDC (`X-Request-Id`) to downstream requests

### 11.2 Image assessment (risk scoring)

Endpoint:

- `POST /api/assess/image` (gateway exposes it as `/api/v1/assess/image`)

Algorithm (as implemented in `AssessmentServiceImpl` and `RiskScoring`):

1) Scan image -> list of findings with CVE IDs and packages
2) Aggregate packages per CVE and detect fix availability per CVE (based on `fixedVersion`)
3) Fetch CVE details and EPSS data from cve-store
4) Compute a per-CVE score:
   - EPSS is treated as 0..1
   - CVSS is normalized by /10
   - Coverage bonus is based on how many packages in the scan map to the CVE:
     `log(1+n)/log(1+cap)` (cap defaults to 10)
   - score formula:
     `perCve = 100 * (wEpss*epss + wCvss*(cvss/10)) * (1 + coverageBonus*coverageNorm)`
5) Choose top K CVEs by per-CVE score (k defaults to 6)
6) Compute overall image score:
   - uses up to top 10 findings
   - weights each CVE by `epss^2` so higher exploitation likelihood dominates
7) Derive a band from overall score:
   - 0-24 LOW, 25-49 MEDIUM, 50-74 HIGH, 75+ CRITICAL
8) Return:
   - overall score + band
   - top findings including URLs and fixAvailable
   - explanation text
   - citations (CVE IDs + URLs + snippets)

Why the scoring works this way (as encoded):

- EPSS is weighted higher than CVSS by default, reflecting "likelihood" as the primary differentiator
  when prioritizing remediation.
- CVSS still contributes as an "impact/technical severity" factor.
- Coverage bonus encourages prioritizing CVEs that appear across more packages/instances in the image.
- Overall score weighting by `epss^2` heavily emphasizes the most exploitable vulnerabilities.

### 11.3 Embeddings and semantic search

Embeddings:

- Generated via Ollama using:
  - preferred endpoint: `/api/embed` (batch)
  - fallback endpoint: `/api/embeddings` (one prompt at a time)
- Dimension is validated against `embeddings.expected-dim` (default 768)

Storage:

- `JdbcVectorStoreRepository` upserts rows in `cve_embeddings` with `chunk_no=0`
- The embedded text is `title + "\n\n" + description` stored as `chunk_text`

Search:

- `EmbeddingSearchRepositoryPg` runs a pgvector query using cosine distance:
  - `ORDER BY embedding <=> q.v LIMIT k`
  - similarity is returned as `1 - distance`
- `VectorSearchService` re-ranks results using a combined score:
  - 70% semantic similarity
  - 20% EPSS
  - 10% CVSS (normalized)

Why this design:

- pgvector allows you to keep the vector index and relational data in one Postgres deployment.
- The combined score is a pragmatic signal blend:
  - semantic similarity finds relevant CVEs to the question
  - EPSS and CVSS bias the ranking toward "important" CVEs among those relevant

### 11.4 QA: follow-up questions

Endpoints (gateway exposes `/api/v1/qa/...`):

- `POST /api/qa/question` (answer a question)
- `GET /api/qa/history` (load saved conversations by assessment scope)
- `DELETE /api/qa/history/{conversationId}` (delete one saved conversation)

Core pattern:

1) Retrieve evidence (top semantic hits from embeddings)
2) Build an evidence text block (CVE IDs + title + truncated description + scores + packages)
3) Call the chat model with assessment-enriched prompt context
4) Return answer + citations
5) Persist user/assistant messages in DB-backed conversation history

Image-aware QA:

- If `imageRef` is provided, ai-service:
  - scans the image (or fetches cached scan)
  - restricts allowed evidence to CVEs present in that scan
  - auto-indexes missing embeddings for those CVEs

Prompt templates:

- Stored under `ai-service/src/main/resources/prompts/*.txt` and loaded at startup.
- They enforce evidence-first grounding, then allow explicit "general guidance" beyond context.

Why this approach:

- The prompts aim for practical, evidence-grounded outputs in a security context.

---

## 12. Deep dive: common module

`common` holds shared types and conventions:

- DTOs (records/classes) used as cross-service API contracts:
  - assessment: `AssessImageRequest`, `AssessImageResponse`, `TopFinding`, `RiskBand`, `Citation`
  - QA: `QaQuestionRequest/Response`
  - CVE/EPSS: `CveEntryDto`, `EpssScoreDto`, `CveForEmbedding`

- Shared error model:
  - `common.error.ErrorResponse` is the gateway-normalized client error envelope

- Common Jackson settings:
  - enable Java time module, ISO timestamps
  - tolerate unknown fields and case-insensitive enums

Why these types exist centrally:

- They reduce drift between services.
- They define the gateway-first contract without requiring a separate OpenAPI codegen step.

---

## 13. Deep dive: frontend

### 13.1 Stack and packaging

- Vite + React + TypeScript
- Material UI (MUI) components
- Keycloak JS for auth
- Built into static assets and served by Nginx in compose

### 13.2 Runtime configuration (config.js)

The Nginx image writes `/config.js` at container startup via
`frontend/docker-entrypoint.d/99-runtime-config.sh`.

This enables runtime configuration of:

- API base URL (`API_BASE_URL`)
- Auth server base URL (`AUTH_BASE_URL`)
- Keycloak realm (`AUTH_REALM`)
- Keycloak client ID (`AUTH_CLIENT_ID`)

The UI reads `window.__RISK_CONSOLE_CONFIG__` first, then falls back to Vite env vars, then to a
default (gateway on `http://localhost:8080`).

Why this matters:

- A single built frontend image can be deployed to different environments without rebuilding.

### 13.3 Authentication flow

- Keycloak realm and client are imported from `keycloak/realm-risk.json` in compose.
- Frontend initializes Keycloak with:
  - `onLoad: "check-sso"`
  - PKCE (`pkceMethod: "S256"`)
  - a silent SSO redirect page (`/silent-check-sso.html`)
- Frontend stores the access token in a module-level variable and adds it as `Authorization: Bearer`
  for API requests.

### 13.4 Gateway-only API usage

The UI uses typed fetch wrappers in `frontend/src/api/client.ts` and calls only:

- `/api/v1/assess/image`
- `/api/v1/qa/question`
- `/api/v1/cves`, `/api/v1/cves/{id}`, `/api/v1/cves/{id}/epss`
- `/api/v1/scans/...`
- `/api/v1/admin/embeddings/...` (admin UI)

---

## 14. Observability and health

### 14.1 Health checks

Compose healthchecks exist for:

- Postgres, Redis, Keycloak
- cve-store, scan-service, gateway

Gateway also exposes:

- `GET /health` which checks the downstream service health endpoints and returns `UP` or `DEGRADED`
  with per-dependency status.

### 14.2 Request correlation

Request ID is propagated:

- Gateway: injects `X-Request-Id` to downstream requests
- scan-service and ai-service: set MDC `requestId` and echo `X-Request-Id` on responses
- ai-service: forwards request ID to downstream WebClient calls

Why it matters:

- When debugging an end-to-end flow (gateway -> ai -> scan/cve-store), `X-Request-Id` can be used to
  correlate logs across services.

---

## 15. Security model (as implemented)

### 15.1 External boundary and auth

- Gateway is the public boundary.
- When enabled, gateway validates JWTs from Keycloak and enforces:
  - authenticated access for `/api/**`
  - admin role for `/api/v1/admin/**`

### 15.2 CORS and proxy trust

- Default config is restrictive.
- `dev` profile is permissive for local development.

### 15.3 Input validation and SSRF-ish risks

- scan-service validates `imageRef` to reduce the risk of passing dangerous values to a shell
  command (Trivy invocation).
- There is no feature that fetches arbitrary external URLs from user input (the system fetches only
  known feeds and well-known endpoints like NVD/EPSS/Ollama).

---

## 16. Why the architecture works end-to-end

The system works as a cohesive pipeline because:

- The gateway provides a single stable interface and centralizes security and error handling.
- scan-service produces a normalized vulnerability representation that downstream logic can depend on
  (and preserves raw scan JSON for audit).
- cve-store-service makes the vulnerability intelligence data queryable and keeps it updated via
  ingestion jobs.
- ai-service composes scanning + enrichment + scoring and also provides a retrieval layer (vector
  search) that grounds LLM outputs in stored evidence.
- the frontend binds these workflows into a UX and relies on Keycloak + gateway to handle auth.

---

## 17. Known limitations and "MVP" characteristics (as of this repo state)

These are not guesses; they are properties of what currently exists in code/config:

- cve-store currently persists only a subset of NVD fields (description and a CVSS base score),
  even though `CveEntryDto` contains many more fields.
- cve-store list paging endpoint does not currently attach EPSS scores (only `getById` does).
- ai-service performs `getByIds` from cve-store as a loop of individual calls (no bulk endpoint).
- gateway routes are primarily YAML-based; there is a conditional Java route config that is not the
  default.
- The repo's roadmap (`ROADMAP.md` and `END_TO_END_PLAN.md`) lists several production-hardening
  items that are not implemented (rate limiting, metrics export, tracing, k8s manifests, etc.).

---

## 18. How to run locally (Compose-centric)

1) Create `.env` from `.env-example` (or ensure these exist):
   - `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`
   - `VITE_API_BASE_URL` (optional, defaults to `http://localhost:8080`)

2) Start the stack:

```bash
docker compose up --build
```

3) Access:

```text
Gateway API:     http://localhost:8080
Swagger UI:      http://localhost:8080/swagger-ui.html
Frontend UI:     http://localhost:5173
Keycloak (via GW): http://localhost:8080/auth/
```

Optional helper:

- `scripts/pull-ollama-models.sh` pulls the configured Ollama models into the `ollama` container.

---

## 19. Pointers to the source of truth (useful file map)

Architecture + runtime wiring:
- `docker-compose.yml`
- `pom.xml`

Gateway:
- `gateway-service/src/main/resources/application.yml`
- `gateway-service/src/main/resources/application-dev.yml`
- `gateway-service/src/main/java/com/finki/vladislavangelovski/gateway_service/filter/*`
- `gateway-service/src/main/java/com/finki/vladislavangelovski/gateway_service/config/*`

Scan:
- `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/api/ScanController.java`
- `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/core/impl/ProcessTrivyInvoker.java`
- `scan-service/src/main/java/com/finki/vladislavangelovski/scan_service/core/impl/JacksonTrivyParser.java`
- `scan-service/src/main/resources/db/migration/V001__create_scan_schema.sql`

CVE store:
- `cve-store-service/src/main/java/com/finki/vladislavangelovski/cve_store_service/batch/IngestionJob.java`
- `cve-store-service/src/main/java/com/finki/vladislavangelovski/cve_store_service/api/CveEntryController.java`
- `cve-store-service/src/main/resources/db/migration/*`

AI:
- `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/service/impl/AssessmentServiceImpl.java`
- `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/qa/*`
- `ai-service/src/main/resources/prompts/*`
- `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/search/*`
- `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/vector/*`
- `ai-service/src/main/java/com/finki/vladislavangelovski/ai_service/history/*`

Frontend:
- `frontend/src/api/client.ts`
- `frontend/src/auth/AuthProvider.tsx`
- `frontend/Dockerfile`
- `frontend/docker-entrypoint.d/99-runtime-config.sh`

CI:
- `.github/workflows/ci.yml`
