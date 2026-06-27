# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**CSAF CMS Backend** — a Spring Boot 4.1 REST API (Java 21) for managing CSAF (Common Security Advisory Framework) security advisory documents. Uses PostgreSQL as its datastore (via Spring Data JPA + Flyway), authenticates via Keycloak (OAuth2/JWT resource server), and supports export to JSON, HTML (Mustache/GraalVM), Markdown (Pandoc), and PDF (WeasyPrint).

## Build & Run Commands

```bash
./mvnw clean package                     # build (includes SBOM generation)
./mvnw spring-boot:run                   # run locally (port 8081)
./mvnw test                              # run all tests
./mvnw test -Dtest=AdvisoryControllerTest                    # single test class
./mvnw test -Dtest=AdvisoryControllerTest#testGetAdvisory    # single test method
./mvnw verify                            # tests + JaCoCo + SpotBugs + Checkstyle
./mvnw -Pgithub-action clean verify      # CI profile (excludes DB-integration tests)
```

Docker: `./mvnw clean package && docker build -f alpine.Dockerfile -t csaf-cms-backend .`

Dev environment (PostgreSQL, Keycloak, validator, nginx): `docker compose -f docker/compose.yaml up`

## Architecture

```
REST API (:8081, /api/v1/)
┌─────────────────────────────────────────────────────────────┐
│ rest/AdvisoryController   — 20+ endpoints: CRUD, workflow,  │
│                             comments, export, templates      │
│ rest/MainController       — /about (version info)           │
│                                                             │
│ service/AdvisoryService   — business logic, role-based ACL  │
│ service/PandocService     — HTML→Markdown (CLI)             │
│ service/WeasyprintService — HTML→PDF (CLI)                  │
│                                                             │
│ json/AdvisoryWrapper      — central domain object           │
│ json/Versioning           — Semantic or Integer strategy    │
│                                                             │
│ service/PostgresRepositoryService — all DB ops via JPA repos│
│ validator/ValidatorServiceClient — calls csaf-validator-service │
│                                                             │
│ config/SecurityConfig     — OAuth2 JWT resource server      │
│ config/CsafRoles          — 8 roles from JWT "groups" claim │
└─────────────────────────────────────────────────────────────┘
```

**Key flows:**
- Advisory lifecycle: `Draft → Review → Approved → RfPublication → Published`
- Versioning: configurable Semantic (CSAF 2.0 compliant) or Integer strategy
- Audit trail: every change stored as JSON Patch (RFC 6902) diffs
- Comments: threaded, referencing specific JSON node IDs in CSAF documents

**Roles:** registered, author, reviewer, editor, publisher, manager, auditor, administrator

## Testing

- **JUnit 5** + **Mockito** for unit tests; **Spring Boot Test** (`@WebMvcTest` + `MockMvc`) for controller tests
- **Testcontainers** for PostgreSQL integration tests (`PostgreSQLExtension` — JUnit 5 Extension)
- CI profile excludes: integration tests that require a live database container
- Test fixtures in `src/test/java/.../fixture/`; sample CSAF docs in `src/test/resources/`
- Coverage target: 95% (enforced via JaCoCo, reported on PRs)

## Key Configuration (`application.properties`)

All values overridable via env vars. Most important:

| Env Var | Purpose | Default |
|---|---|---|
| `CSAF_CMS_BACKEND_PORT` | Server port | `8081` |
| `CSAF_DB_HOST/PORT/NAME/USER/PASSWORD` | PostgreSQL connection | `localhost:5432/csaf/csaf/csaf` |
| `CSAF_OIDC_ISSUER_URL` | Keycloak realm issuer | `http://localhost/realms/csaf` |
| `CSAF_VALIDATION_BASE_URL` | Validator service URL | (empty) |
| `CSAF_VERSIONING` | `Semantic` or `Integer` | `Semantic` |
| `CSAF_TEMPLATES_FILE` | Path to document templates JSON | (empty) |

Also loads `optional:file:.env[.properties]` for local overrides.

## Code Quality

- **Checkstyle** (`checkstyle.xml`) — runs at `validate` phase, fails on error
- **SpotBugs** — runs at `verify`, effort=Max, threshold=High
- **JaCoCo** — coverage report in `target/jacoco-report/`
- **Conventional Commits** — `<type>(<scope>): <subject>` (types: feat, fix, docs, style, refactor, perf, test, chore)

## Project Structure

```
src/main/java/de/bsi/secvisogram/csaf_cms_backend/
├── config/          — Spring config, security, roles, versioning settings
├── couchdb/         — Legacy field enums and exceptions (retained for wrapper compatibility)
├── entity/          — JPA entities (AdvisoryEntity, CommentEntity, audit trail, etc.)
├── exception/       — Domain exceptions with error codes
├── json/            — Domain wrappers, versioning strategies, audit trail
├── model/           — Enums (WorkflowState, ExportFormat, filter expressions)
├── mustache/        — GraalVM JS-based Mustache HTML export
├── repository/      — Spring Data JPA repository interfaces
├── rest/            — Controllers, request/response DTOs
├── service/         — Business logic, workflow, PostgresRepositoryService bridge
└── validator/       — CSAF validator service HTTP client
```

## Documentation

The `documents/` directory contains the full architecture documentation:
- `architecture-decisions.md` — arc42-structured architecture document (goals, constraints, context, building blocks, runtime, data model, workflow states, versioning, audit trail, OWASP mitigations)
- `restservices.md` — CSAF 2.0 conformance requirements mapped to REST services
- `owasp-top-10.md` — OWASP Top 10:2021 risk responses
- `*.drawio.svg` — Architecture diagrams, state machines, sequence diagrams, data model

## CI/CD

GitHub Actions (`.github/workflows/github-actions.yml`): triggered on PRs to `main`
- Builds with `github-action` Maven profile (JDK 21, excludes CouchDB tests)
- Publishes test results, JaCoCo coverage comment, coverage badge
- Markdown lint on all `*.md` files
- Dependabot for weekly Maven dependency updates