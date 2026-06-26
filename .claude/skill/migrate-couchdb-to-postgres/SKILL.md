---
name: migrate-couchdb-to-postgres
description: >
  Orchestrator for the phased migration of the CSAF CMS Backend from CouchDB (IBM Cloudant SDK) to PostgreSQL (Spring Data JPA).
  Invokes individual phase skills in order. Each phase can also be run standalone.
user-invocable: true
allowed-tools: Agent, Read, Write, Edit, Glob, Grep, Bash, AskUserQuestion, TaskCreate, TaskUpdate, TaskList, EnterPlanMode, ExitPlanMode
---

# Migrate CouchDB to PostgreSQL — Orchestrator

You are orchestrating a multi-phase migration of the CSAF CMS Backend persistence layer from CouchDB to PostgreSQL.

## Pre-flight

Before starting, read these files to refresh your understanding of the current state:

- `CLAUDE.md` — project overview
- `src/main/java/de/bsi/secvisogram/csaf_cms_backend/couchdb/CouchDbService.java` — current repository
- `src/main/java/de/bsi/secvisogram/csaf_cms_backend/service/AdvisoryService.java` — primary consumer
- `src/main/java/de/bsi/secvisogram/csaf_cms_backend/json/ObjectType.java` — document types
- `src/main/resources/application.properties` — current config
- `pom.xml` — current dependencies

Then present the user with a summary of what will change and ask for confirmation before proceeding.

## Phases

The migration is split into 9 phases. Each phase has its own skill file in this directory:

| Phase | Skill file | Summary |
|---|---|---|
| 1 | `phase1-schema.md` | Design and create the PostgreSQL schema (Flyway) |
| 2 | `phase2-dependencies.md` | Update pom.xml (add JPA/PG/Flyway, remove Cloudant) |
| 3 | `phase3-entities.md` | Create JPA entity classes |
| 4 | `phase4-repositories.md` | Create Spring Data repository interfaces |
| 5 | `phase5-service-rewrite.md` | Rewrite AdvisoryService to use repositories |
| 6 | `phase6-configuration.md` | Update application.properties, Docker, env vars |
| 7 | `phase7-tests.md` | Replace CouchDB test infra with PostgreSQL Testcontainers |
| 8 | `phase8-data-migration.md` | Optional: one-time CouchDB→PostgreSQL data migration |
| 9 | `phase9-cleanup.md` | Delete old code, update documentation |

## Execution Order

Create a task list and work through the phases in order. Each phase should compile and (where possible) pass tests before moving to the next:

```
Phase 2 (deps) → Phase 1 (schema) → Phase 3 (entities) → Phase 4 (repos)
    → Phase 5 (service rewrite) → Phase 6 (config) → Phase 7 (tests)
    → Phase 9 (cleanup)
```

Phase 8 is optional — ask the user if they need it.

After each phase, run `./mvnw compile` (or `./mvnw test` where applicable) to catch issues early.

## Known Issues & Lessons Learned

These were discovered during execution and must be accounted for:

### Checkstyle: Import ordering

The project's `checkstyle.xml` enforces `CustomImportOrder` with rule `STATIC###THIRD_PARTY_PACKAGE`. This means:
- **All imports in a single group**, sorted alphabetically (no blank lines between groups)
- Third-party packages (`com.*`, `jakarta.*`, `org.*`) sort **before** `java.*` because they come first alphabetically
- **No blank line** separating third-party from java imports
- Static imports go in their own group at the top (separated by blank line)

**Correct:**
```java
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
```

**Wrong (will fail checkstyle):**
```java
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Entity;
```

### Testcontainers PostgreSQL: version not managed by BOM

The Spring Boot 4.1.0 BOM manages `testcontainers-junit-jupiter` but does NOT manage `testcontainers:postgresql`. You must use `${testcontainers.version}` (provided by the parent BOM) explicitly:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>
```

### JPA auto-configuration breaks existing tests

Adding `spring-boot-starter-data-jpa` causes Spring Boot to auto-configure a DataSource. During migration (while CouchDB code still exists), tests will fail with `Failed to determine a suitable driver class` unless JPA is disabled in tests.

**Temporary fix** (in `src/test/resources/application.properties`):
```properties
spring.autoconfigure.exclude=org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,\
  org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,\
  org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
```

This must be **removed** in Phase 7 when proper PostgreSQL Testcontainers support is added.

### Pre-existing test failures (not caused by migration)

6 tests fail in the default profile because they use CouchDB Testcontainers and Docker is not available:
- `CouchDbServiceTest`
- `AdvisorySearchUtilTest`
- `AdvisoryServiceExportNoLogoTest`
- `AdvisoryServiceTest`
- `AdvisoryWorkflowIntegerVersioningTest`
- `AdvisoryWorkflowSemanticVersioningTest`

These are already excluded in the `github-action` CI profile. Use `mvn verify -Pgithub-action` for a clean verification during migration.

### Annotations must be on separate lines

Checkstyle `AnnotationLocation` rule: `allowSamelineParameterizedAnnotation=false`. Every annotation must be on its own line — no `@Id @GeneratedValue` on the same line.

## Important Constraints

- **Do not change the REST API contract** — all existing endpoints, request/response shapes, and HTTP status codes must remain identical.
- **Preserve the CSAF JSON structure** — store it as JSONB, do not normalize it into relational tables.
- **Keep `@Version` optimistic locking** — this replaces CouchDB's `_rev` conflict detection.
- **Maintain audit trail behavior** — every create/update/delete/workflow-change must still produce audit trail records.
- **Match existing code style** — follow the project's Checkstyle rules, comment density, and naming conventions.
- **Conventional commits** — use `feat(postgres):`, `refactor(postgres):`, `test(postgres):` etc.