# Phase 9 — Cleanup & Documentation

Final cleanup after all other phases are complete and verified.

## Code Cleanup

1. **Delete** the entire `couchdb/` package:
   - `src/main/java/de/bsi/secvisogram/csaf_cms_backend/couchdb/` (all files)

2. **Remove** IBM Cloudant SDK from `pom.xml`:
   - Remove `<dependency>` for `com.ibm.cloud:cloudant`
   - Remove any `<dependencyManagement>` entry for it

3. **Delete** the old test infrastructure:
   - `src/test/java/.../CouchDBExtension.java`

4. **Remove** any remaining CouchDB-specific imports or dead code across the project:
   ```bash
   grep -r "couchdb\|cloudant\|CouchDb" src/ --include="*.java" -l
   ```

## Documentation Updates

### Update `CLAUDE.md`

- Replace all CouchDB references with PostgreSQL
- Update the architecture diagram
- Update the "Key Configuration" table with new env vars
- Update build/run commands if needed
- Update testing section (no more CouchDB exclusions)

### Update `documents/architecture-decisions.md`

Add an ADR (Architecture Decision Record) for the migration:

```markdown
## ADR: Replace CouchDB with PostgreSQL

### Status: Accepted

### Context
The application used CouchDB as its document store via the IBM Cloudant SDK.
All 7 document types were stored in a single database, discriminated by a `type` field.

### Decision
Migrate to PostgreSQL with Spring Data JPA:
- Normalized relational schema (7 tables)
- CSAF document content stored as JSONB (preserving schema-less nature)
- Flyway for schema migrations
- @Version optimistic locking (replaces CouchDB _rev)
- JPA Specifications for dynamic queries (replaces Mango selectors)

### Consequences
- Atomic counter increments (no more race conditions)
- CASCADE deletes simplify advisory deletion
- GIN indexes on JSONB enable efficient content queries
- Standard tooling (pgAdmin, psql, Flyway) replaces CouchDB-specific tooling
- Testcontainers PostgreSQL replaces custom CouchDB test extension
```

### Update `README.md` / setup documentation

- Update prerequisites (PostgreSQL instead of CouchDB)
- Update Docker Compose instructions
- Update environment variable reference

### Update `documents/restservices.md`

- If it references CouchDB internals, update accordingly

## Final Verification

Run the full verification suite:

```bash
./mvnw clean verify
```

This runs:
- All tests (JUnit 5 + Testcontainers PostgreSQL)
- JaCoCo coverage check (95% target)
- SpotBugs (effort=Max, threshold=High)
- Checkstyle

All must pass before the migration is considered complete.

## Git

Commit with:
```
feat(postgres): complete CouchDB to PostgreSQL migration

- Replace IBM Cloudant SDK with Spring Data JPA + PostgreSQL
- Add Flyway schema migrations
- Implement JPA entities with JSONB support for CSAF documents
- Replace Mango queries with JPA Specifications
- Add atomic counter increment (fixes race condition)
- Replace CouchDB Testcontainers with PostgreSQL Testcontainers
- Update Docker Compose and configuration
- Update documentation

BREAKING CHANGE: CouchDB is no longer supported. Use the data migration
tool (--spring.profiles.active=migrate-couchdb) to migrate existing data.
```