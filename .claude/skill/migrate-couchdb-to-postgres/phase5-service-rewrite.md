# Phase 5 — Rewrite CouchDbService → PostgreSQL-backed Service

This is the core migration step.

## Architecture Change

```
Current:   AdvisoryController → AdvisoryService → CouchDbService (CouchDB)
Target:    AdvisoryController → AdvisoryService → Spring Data Repositories (PostgreSQL)
```

## Strategy: Replace CouchDbService calls in AdvisoryService

Go through `AdvisoryService.java` method by method and replace each `couchDbService.*` call:

| Current CouchDbService call | Replacement |
|---|---|
| `writeDocument(id, json)` | `repository.save(entity)` |
| `updateDocument(json)` | `repository.save(entity)` (with `@Version` check) |
| `readDocumentAsStream(id)` | `repository.findById(id)` |
| `findDocuments(selector, fields)` | `repository.findBy*()` or `Specification` query |
| `findDocumentsAsStream(selector, fields)` | Same, but return `Stream<Entity>` |
| `deleteDocument(id, rev)` | `repository.deleteById(id)` |
| `bulkDeleteDocuments(ids)` | `repository.deleteAllById(ids)` or cascade delete |

## Key Refactoring Patterns

1. **Remove all Jackson `ObjectNode`/`JsonNode` manipulation** for metadata fields (workflowState, owner, etc.) — these are now typed entity fields.
2. **Keep `csaf` as `JsonNode`** — the CSAF document tree stays as JSONB, preserving the schema-less nature of CSAF content.
3. **Replace `_rev` checks with `@Version`** — optimistic locking is handled by JPA. Catch `OptimisticLockingFailureException` where the code currently checks for revision conflicts.
4. **Cascade deletes** — when deleting an advisory, let `ON DELETE CASCADE` handle audit trails, comments, and versions instead of manual bulk-delete loops.
5. **Visibility expressions** — rewrite `AdvisoryWorkflowUtil.buildVisibilityExpression()` to return a `Specification<AdvisoryEntity>` instead of a Mango selector.

## Files to Modify

- `AdvisoryService.java` — main rewrite target
- `AdvisoryWorkflowUtil.java` — visibility expressions, helper methods
- `AdvisorySearchUtil.java` — search filter assembly (Mango → Specification)

## Files to Delete When Done

- `couchdb/CouchDbService.java`
- `couchdb/CouchDBFilterCreator.java`
- `couchdb/CouchDbField.java`, `AdvisoryField.java`, `AdvisorySearchField.java`
- `couchdb/CommentField.java`, `AuditTrailField.java`, `AdvisoryAuditTrailField.java`, `CommentAuditTrailField.java`
- `couchdb/DatabaseException.java` (replace with Spring's `DataAccessException`)
- `couchdb/IdNotFoundException.java` (replace with `EntityNotFoundException` or keep as custom)
- `couchdb/DbField.java`

## Wrapper Classes — Decision Point

The `json/` package wrappers (`AdvisoryWrapper`, `CommentWrapper`, `AuditTrailWrapper`, etc.) currently wrap raw `ObjectNode` and provide accessor methods. Two options:

- **Option A (recommended):** Keep wrappers as adapters between entities and the controller DTOs — they convert `AdvisoryEntity` ↔ response JSON. Refactor them to accept/produce entity objects.
- **Option B:** Eliminate wrappers entirely and map directly entity → DTO. More work but cleaner long-term.

**Ask the user which option they prefer before proceeding.**

## Migration Approach

To keep the project compilable during migration, consider this incremental approach:

1. First, create a new `AdvisoryRepositoryService` that wraps the Spring Data repositories with the same method signatures as `CouchDbService`.
2. Replace `CouchDbService` injection with `AdvisoryRepositoryService` in `AdvisoryService`.
3. Gradually refactor `AdvisoryService` to use entities directly (removing the intermediate wrapper).
4. Delete `CouchDbService` and `AdvisoryRepositoryService` once all methods are migrated.

## Verification

After completing this phase:
- `./mvnw compile` must pass
- `./mvnw test -Dtest=AdvisoryControllerTest` should pass (if mocking is at the service level)