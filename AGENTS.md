# AGENTS.md — Docker Image Risk Assessment Application

> Audience: autonomous and semi-autonomous coding agents (e.g., Codex).
> Goal: enable safe, consistent, minimal-risk contributions to this repo.
> Constraint: treat the repository as the source of truth; do not assume patterns not present in code.

---

## 0) Prime directive

1) **Read first, then act.** Before changing anything, locate the exact files involved and read them end-to-end.
2) **Make the smallest correct change.** Prefer minimal diffs and incremental commits over refactors.
3) **Keep the system runnable.** After changes: build, run tests relevant to the change, and ensure services still start.
4) **Don’t invent API shapes.** Verify endpoints and DTOs from code (controllers + OpenAPI) before updating UI/docs.
5) **Never commit secrets.** No API keys, tokens, private URLs, credentials, or personal data.

If an instruction conflicts with existing code or build constraints, **follow the repo**, and document the mismatch.

---

## 1) What this project is

A **Docker image risk assessment** application composed of Spring Boot services:

- **gateway-service**: single public entrypoint; routing, auth (API key), error normalization
- **scan-service**: runs an image scanner (Trivy) and produces raw + normalized results
- **cve-store-service**: ingests and serves CVE + EPSS data (DB-backed)
- **ai-service**: computes risk summaries and provides RAG-based Q&A (claim/question) with evidence
- **common**: shared DTOs/models/config used across services

Core workflows:

1) **Assess image**: `imageRef` → scan → enrich with CVE/EPSS → compute risk score/band → return top findings + evidence
2) **RAG QA**: claim/question → retrieve relevant CVEs/EPSS → LLM summarization/answer → return answer + citations/evidence

---

## 2) Technology & constraints (source-of-truth)

- **Java**: **21**
- **Spring Boot**: 3.x
- **Build**: Maven multi-module
- **Data**: PostgreSQL (including vectors if configured, e.g., pgvector), Redis
- **Local orchestration**: Docker Compose
- **Production target**: Kubernetes (manifests/Helm planned)
- **Scanner**: Trivy (invoked by scan-service)
- **Embeddings/LLM runtime**: local (e.g., Ollama) or configurable provider (verify in config)

**Do not assume versions** beyond what is pinned in `pom.xml` and CI workflows.

---

## 3) Repo map (how to orient yourself)

Before any change:
- Read **root `pom.xml`** to see active modules, plugins, and enforced versions.
- Locate each service root (look for `*/pom.xml` with `spring-boot-starter-parent` or `spring-boot-maven-plugin`).
- Find service entrypoints under:
  - `src/main/java/**/Application.java` (or similar)
  - Controllers under `src/main/java/**/controller` (or equivalent)
- Config lives in `src/main/resources/application*.yml` or `.properties`.

If a `frontend/` module exists (or is added later), treat it as an independent build with its own toolchain, but it must call **only the gateway**.

---

## 4) How agents should work in this repo

### 4.1 Default workflow (safe path)
1) Identify the change request.
2) Locate affected module(s) via search (DTOs, controllers, services, config).
3) Read the full existing implementation (do not patch “blindly”).
4) Draft a short plan:
   - files to change
   - expected behavior
   - how you will validate
5) Implement minimal changes.
6) Validate:
   - compile/build
   - unit tests (and integration tests if present)
   - run the relevant service(s) via Compose if required
7) Update docs/tests if behavior changed.

### 4.2 Output expectations (when interacting with humans)
- Provide **step-by-step** explanation.
- Avoid dumping large code blocks unless explicitly requested.
- When you must provide code, show only the diff or the smallest relevant snippet.

---

## 5) Build & test (Windows-friendly)

This repo is often used on **Windows 11** with **IntelliJ IDEA Ultimate**.

### 5.1 Maven
Prefer the Maven Wrapper if present:
- `.\mvnw.cmd -v`
- `.\mvnw.cmd clean verify`

If no wrapper exists:
- `mvn -v`
- `mvn clean verify`

### 5.2 Targeted builds
When working on one module, prefer targeted builds (verify actual module artifactIds in the root POM):
- `mvn -pl <module> -am clean verify`

### 5.3 Docker Compose
Use Docker Desktop.
- `docker compose up --build`
- `docker compose ps`
- `docker compose logs -f <service>`

> Avoid Linux-specific shell assumptions. Keep commands copy-pasteable on Windows PowerShell.

---

## 6) API & contracts

### 6.1 Gateway is the contract boundary
- External clients (and the future frontend) must call **only the gateway**.
- Services may evolve internally, but gateway routes + DTOs must remain stable or be versioned.

### 6.2 DTO ownership
- Shared request/response models should live in **common**.
- Service-specific internal models can live in the service, but crossing-service payloads should not duplicate types.

### 6.3 Error model
Prefer a consistent error payload across services/gateway:
- timestamp / request id (if available)
- HTTP status
- short message
- path / endpoint
- optional details (validation errors)

Do not leak internal stack traces to clients.

---

## 7) Data ingestion & persistence

### 7.1 CVE/EPSS ingestion
- Treat ingestion as **idempotent** (re-running should not corrupt data).
- Store provenance (source, import time) if the schema supports it.
- Avoid breaking migrations: never edit applied migrations; add new ones.

### 7.2 DB migrations
- Follow the existing migration tool (Flyway or Liquibase, whichever is configured).
- Add new migrations with incremental versioning and clear names.
- Keep migrations reversible where possible.

---

## 8) Scanner integration (scan-service)

- The scanner output format can change between versions; keep parsers robust.
- Preserve:
  - the **raw** scan output (for debugging/auditing)
  - the **normalized** output (for stable downstream logic)
- When modifying parsing:
  - add/extend fixtures for sample outputs
  - write tests that validate normalization on real-like payloads

---

## 9) AI/RAG (ai-service)

- Retrieval must always return **evidence** (even if limited).
- Prefer deterministic prompt templates stored in code/resources.
- Keep “explainability”:
  - why a risk band was chosen
  - what evidence supports the answer
- Do not hardcode model endpoints/keys; use configuration.

If you change embeddings schema/indexing:
- provide a migration path
- update any admin endpoints
- update docs on how to re-index safely

---

## 10) Frontend phase guidance (if/when present)

If a `frontend/` app exists or is introduced:
- It must call the gateway only.
- Keep API key handling explicit and demo-safe (session storage preferred over local storage).
- Include a “Raw JSON” view in UI for auditability.
- Minimal pages:
  - Dashboard
  - Image assessment
  - QA (claim/question)
  - CVE lookup

---

## 11) Security rules (non-negotiable)

- Never commit secrets. Use:
  - `.env.example` templates
  - environment variables
  - Docker Compose secrets/configs where available
- Avoid SSRF-style behavior:
  - validate and restrict external URLs if any feature fetches remote content
- Validate user input:
  - `imageRef`
  - query strings and paging params
  - claim/question text sizes and rate limits (gateway)
- Prefer allowlists over denylists.

---

## 12) Logging, metrics, observability

- Use structured logging where already present.
- Include correlation/request IDs from gateway through downstream services if supported.
- Do not log sensitive values (API keys, tokens, personal data).
- Expose health endpoints but protect sensitive actuator endpoints in prod.

---

## 13) Code style & conventions

- Follow existing package naming, layering, and patterns.
- Prefer constructor injection.
- Prefer immutable DTOs where feasible.
- Keep controller logic thin; business logic in services.
- Add tests for:
  - parsing/normalization
  - risk scoring edge cases
  - ingestion idempotency

If formatting tools exist (Spotless/Checkstyle), run them and do not fight them.

---

## 14) Change checklist (before you claim “done”)

- [ ] I verified the exact existing API/DTO shapes in code.
- [ ] I made the smallest change that satisfies the requirement.
- [ ] `mvn clean verify` (or module-targeted verify) succeeds.
- [ ] Tests updated/added for the new behavior.
- [ ] No secrets added; configs remain externalized.
- [ ] Docs updated if public behavior changed (README/OpenAPI/examples).
- [ ] Markdown files updated if needed.

---

## 15) If you get stuck

1) Re-read configuration files for the relevant service(s).
2) Check gateway routes and ports (Compose + application config).
3) Inspect service logs via `docker compose logs -f`.
4) Confirm DB is reachable and migrations applied.
5) Reduce the reproduction to the smallest failing request.

Document what you learned in a short note (issue/PR description).

---

**If you’re an agent: prioritize correctness, reproducibility, and minimal diffs.**
