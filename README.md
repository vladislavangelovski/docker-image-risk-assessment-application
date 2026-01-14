# Docker Image Risk Assessment Application

## Introduction
This project contains multiple microservices that together provide vulnerability risk assessment:
- **cve-store-service:** Ingests and persists CVE & EPSS data
- **scan-service:** Scans container images against stored CVE data
- **ai-service:** Provides AI-powered analysis and recommendations
- **gateway-service:** API gateway and routing for the other services

## Architecture Overview
- Services communicate over an internal Docker network
- **gateway-service** routes external REST calls to the appropriate backends
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
  - retrieved_at

## Prerequsites
- Docker & Docker Compose (v1.29+)
- Java 21 (for local build/tests)
- Maven 3.9+

## Environment Variables
Create a `.env` in the repo root with
```
POSTGRES_USER=risk
POSTGRES_PASSWORD=dev
POSTGRES_DB=riskdb
INGEST_ENABLED=true
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
```
Optional (AI embeddings startup indexing):
```
EMBEDDINGS_STARTUP_ENABLED=true
EMBEDDINGS_STARTUP_BATCH_SIZE=20
EMBEDDINGS_STARTUP_MAX_BATCHES=5
```

## Local Development
1. **Start containers**
`docker compose up --build` or `docker compose up --build -d` to make it detached
2. **Access Services**
    - CVE Store API: `http://localhost:8080/api/v1/cves`
    - Swagger UI: `http://localhost:8080/swagger-ui.html`

### Startup order & health
- Postgres and Redis must be healthy before the app services start consuming them.
- `cve-store` and `scan-service` expose `/actuator/health` (with DB/Redis checks) and Docker healthchecks keep them from becoming "ready" until their backing stores respond.
- `ai-service` waits for both `cve-store` and `scan-service` to report healthy before starting, reducing startup-race failures.
- The CVE bootstrap runner in `cve-store` waits for the database to answer before running the initial ingest so first-time startup is reliable.

## Running Tests
```
# Run all unit + integration tests
docker compose run --rm cve-store-service mvn test
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
