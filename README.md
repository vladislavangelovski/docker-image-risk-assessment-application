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
- Java 23 (for local build/tests)
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

## Local Development
1. **Start containers**
`docker compose up --build` or `docker compose up --build -d` to make it detached
2. **Access Services**
    - CVE Store API: `http://localhost:8080/api/v1/cves`
    - Swagger UI: `http://localhost:8080/swagger-ui.html`

## Running Tests
```
# Run all unit + integration tests
docker compose run --rm cve-store-service mvn test
```
