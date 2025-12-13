# Project status and next-step course

## Current implementation (by service)
- **cve-store-service**: Scheduled `IngestionJob` pulls recent NVD CVEs and daily EPSS CSV, upserting through the service layer with startup bootstrap support. Flyway manages schema for CVE/EPSS tables and an embeddings table. REST controllers expose paging, EPSS lookup, and save operations backed by JPA entities, repositories, and MapStruct mappers.
- **scan-service**: `ScanController` offers synchronous scan submission and retrieval with raw/normalized options. `DefaultScanOrchestrator` drives Trivy via `ProcessTrivyInvoker`, normalizes output with `JacksonTrivyParser`, persists results with `JdbcScanPersistence`, and caches them in Redis (or in-memory fallback) using `ScanCache`.
- **ai-service**: `AssessmentServiceImpl` calls the scan service to obtain findings, fetches CVE details via `CveStoreClient`, computes weighted risk scores, and returns top findings with citations and risk bands. It also hosts semantic search/QA endpoints backed by pgvector (`JdbcVectorStoreRepository`) and an embeddings client (Ollama by default). Web clients and configs are wired for downstream HTTP calls and AI chat defaults.
- **common**: Shared DTOs/models for CVE data, scan assessment payloads, QA responses, and MapStruct mappers to keep contracts consistent across services.
- **gateway-service**: Spring Boot entrypoint exists but no routes, filters, or security are configured yet.
- **docker-compose**: Brings up Postgres with pgvector, Redis, Ollama, and all services with environment wiring; AI service targets scan and CVE endpoints over the compose network.

## Service wiring review
- **Ollama connectivity**: The AI service now targets the `ollama` container on the compose network (previously pointed to `host.docker.internal`, which breaks on Linux hosts). Update local `.env`/deployments accordingly.
- **Gateway**: No routes are present yet, so external traffic still hits services directly; add Spring Cloud Gateway routing and shared filters before exposing the stack.
- **Dependencies**: Compose waits for Postgres before starting CVE Store/AI but only uses `service_started` for CVE Store and Scan Service; prefer health-based conditions (or retries) to avoid cold-start call failures.

## Course to reach the goal with current context
1. **Harden service edges and gateway**
   - Add Spring Cloud Gateway routes for `/api/v1/cves/**`, `/api/v1/scans/**`, and AI endpoints; include request logging, CORS, and timeouts sized for scans.
   - Introduce authentication/authorization (API key or OAuth2/JWT) at the gateway and propagate identities to services; ensure TLS for external entry.
   - Add rate limiting and circuit breaking (Resilience4j) for downstream CVE/scan/AI calls.

2. **Reliability and observability across services**
   - Standardize OpenTelemetry tracing, correlation IDs, and JSON logging; export Prometheus metrics (ingestion lag, scan durations, AI latency) with health probes.
   - Add retries/backoff and alerting around NVD/EPSS pulls, scan invocation, and AI/embeddings calls; surface failure metrics and dead-letter queues if messaging is introduced.

3. **Workflow orchestration and async UX**
   - Keep synchronous scan for MVP, but add background scan + webhook/polling support with status endpoints so AI assessments can trigger on completion.
   - Optionally publish scan-completed events (Kafka/RabbitMQ) consumed by AI service to decouple assessments from user requests and enable batching.

4. **Data quality and enrichment**
   - Tighten validation and conflict handling in ingestion (idempotent upserts, checksum on EPSS feeds, pagination limits) and expose richer CVE query filters (severity, date, EPSS band, CPE search) with indexes.
   - Extend scan normalization to cover additional Trivy artifact types and capture vendor/NVD CVSS, fixed versions, and package ecosystems consistently.

5. **AI assessment robustness**
   - Add caching of assessment responses keyed by image digest + options; include rate limits and graceful degradation when LLM/embeddings are unavailable.
   - Provide prompt templates and safety/PII guards; persist assessment outputs with versioning to align with scan results and enable audit trails.

6. **CI/CD and developer experience**
   - Set up pipeline to run module tests, Flyway migrations, and a lightweight compose smoke test; publish container images with tags.
   - Provide seed scripts/sample requests for local dev (Trivy fixtures are present) and document running with/without Ollama.

7. **Documentation and API contracts**
   - Generate and expose OpenAPI/Swagger for all services (controllers already annotated in scan service); add diagrams for ingest→scan→AI flow and guidance on configuring secrets.
