# Docker Image Risk Assessment Application

## Introduction
This project contains multiple microservices that together provide vulnerability risk assessment:
- **cve-store-service:** Ingests and persists CVE & EPSS data
- **scan-service:** Scans container images against stored CVE data
- **ai-service:** Provides AI-powered analysis and recommendations
- **gateway-service:** API gateway and routing for the other services
- **frontend:** Web UI (talks to the gateway only)

## Architecture Overview
- Services communicate over an internal Docker network
- **gateway-service** routes external REST calls to the appropriate backends and is the public entrypoint
- Each service exposes a HTTP port and a health endpoint

## Database Schema
### Key Entities
- CveEntry (cve_entry)
  - cve_id (PK)
  - description
  - published_date
  - modified_date
  - cvss_v3_score
- EpssScore (epss_score)
  - id (PK)
  - cve_id (FK -> cve_entry.cve_id)
  - score
  - percentile
  - retrieved_at (latest-only; one row per cve_id)

## Prerequisites
- Docker & Docker Compose (v1.29+)
- Java 21 (for local build/tests)
- Maven 3.9+
- Node.js + npm (only for local frontend development)

## Environment Variables
Copy `.env-example` to `.env` in the repo root and fill it in:
```
POSTGRES_USER=risk
POSTGRES_PASSWORD=dev
POSTGRES_DB=riskdb
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<set-a-strong-password>
VITE_API_BASE_URL=http://localhost:8080
```
Notes:
- `VITE_API_BASE_URL` is used by the frontend build to target the gateway.
- The compose stack runs Keycloak (proxied via the gateway at `/auth`) and enables JWT auth at the gateway.
- If `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` are not set, docker-compose defaults them to `admin` / `admin` for local development.

Optional (AI embeddings startup indexing):
```
EMBEDDINGS_STARTUP_ENABLED=true
EMBEDDINGS_STARTUP_BATCH_SIZE=20
EMBEDDINGS_STARTUP_MAX_BATCHES=5
```

Optional (AI web search for remediation links):
```
WEBSEARCH_ENABLED=true
WEBSEARCH_BRAVE_API_KEY=<your_brave_search_api_key>
# Optional: WEBSEARCH_TRIGGER=cve_only|image_or_cve|always
# Optional: WEBSEARCH_MAX_RESULTS=5
```

Optional (compose image assessment parallelism):
```
# Keep at 1 to avoid Trivy DB cache lock contention on compose assessments
AI_COMPOSE_MAX_PARALLEL_IMAGE_ASSESSMENTS=1
```

## Local Development
1. **Start containers**
`docker compose up --build` or `docker compose up --build -d` to make it detached
2. **Access Services**
    - Gateway API: `http://localhost:8080`
    - Swagger UI (aggregated): `http://localhost:8080/swagger-ui.html`
    - Frontend UI: `http://localhost:5173`
    - Keycloak (via gateway): `http://localhost:8080/auth/`
    - Keycloak admin console: `http://localhost:8080/auth/admin/`

Authentication note:
- The UI uses Keycloak (OIDC) for login. If you don’t have a user yet, register one from the Keycloak login screen (registration is enabled for the `risk` realm).

### Security defaults (gateway)
- **CORS**: locked down by default (`allowedOrigins: []`); the `dev` profile opens it up for local development (`gateway-service/src/main/resources/application-dev.yml`).
- **Trusted proxies**: locked down by default (localhost-only); the `dev` profile allows all proxies for local compose.
- **Auth**: JWT authentication/authorization at the gateway (Keycloak-backed in docker-compose). Admin routes under `/api/*/admin/**` require the `admin` role.

### Sensitive payload logging (scan-service)
- Request payload logging is **off by default** (`scan-service/src/main/resources/application.yml` → `debug.http.log-requests=false`).

### Startup order & health
- Postgres and Redis must be healthy before the app services start consuming them.
- `cve-store` and `scan-service` expose `/actuator/health` (with DB/Redis checks) and Docker healthchecks keep them from becoming "ready" until their backing stores respond.
- `ai-service` waits for both `cve-store` and `scan-service` to report healthy before starting, reducing startup-race failures.
- The CVE bootstrap runner in `cve-store` waits for the database to answer before running the initial ingest so first-time startup is reliable.

### End-to-end (gateway-only)
- Image scan: `POST http://localhost:8080/api/v1/scans`
- Image assessment: `POST http://localhost:8080/api/v1/assess/image`
- QA question: `POST http://localhost:8080/api/v1/qa/question`
- QA claim: `POST http://localhost:8080/api/v1/qa/claim`
- QA history: `GET http://localhost:8080/api/v1/qa/history`

QA note:
- When you pass `imageRef`, the AI service will **auto-index missing CVE embeddings** for the CVEs found in that image scan (first request may take longer).
- For semantic-only queries without an `imageRef`, you can pre-index using `POST /api/v1/admin/embeddings/index`.
- The default Ollama chat model is configured in `ai-service/src/main/resources/application.yml`. First QA request can be slow if the model has to be pulled or is cold-starting.

### Groq (fast hosted chat) instead of Ollama
The AI service can use Groq (OpenAI-compatible) for **chat** while keeping Ollama for **embeddings**.

1. Set env vars (e.g. in `.env`):
   - `AI_SERVICE_PROFILES=dev,groq`
   - `GROQ_API_KEY=<your_groq_api_key>`
   - Optional: `GROQ_CHAT_MODEL=llama-3.1-8b-instant` (default is `llama-3.3-70b-versatile`)
2. Restart the stack (or just `ai-service`):
   - `docker compose up -d --build ai-service`

## Dev seed data strategy
Use a small, reproducible dataset for local demos instead of the full NVD/EPSS feed.

- Reduce the CVE ingest window by lowering `ingest.nvd.lookback-days` in `cve-store-service/src/main/resources/application.yml`
  (or override it via environment variables).
- Trigger a one-off ingest through the gateway:
  - `POST /api/v1/admin/ingestion/nvd`
  - `POST /api/v1/admin/ingestion/epss`
  - Full historical NVD bootstrap: `POST /api/v1/admin/ingestion/bootstrap` (optionally `?epss=false`)
- Seed embeddings with a short CVE list (or let the AI service auto-index from an image scan):
  - `POST /api/v1/admin/embeddings/index` with `{ "cveIds": ["CVE-2024-XXXX", "CVE-2025-YYYY"] }`
- Use a small public image (e.g. `alpine:3.19`, `nginx:1.25`) to generate a scan and inspect it in the UI.

## Running Tests
```
# Run all unit + integration tests
./mvnw clean verify
```

## Handling merge conflicts when updating a PR
1. **Sync with the latest base branch**
   ```bash
   git fetch origin
   git checkout work  # or your feature branch
   git rebase origin/main
   ```
   Resolve any conflicts shown during the rebase. Use `git status` to confirm only resolved files remain.
2. **Verify builds still pass**: run the relevant Maven module packages/tests and, if applicable, rebuild Docker images locally.
3. **Update the PR branch**: force-push after the rebase to refresh the PR state.
   ```bash
   git push --force-with-lease origin work
   ```
4. **Re-request reviews** if reviewers were previously assigned so they see the conflict resolution changes.
