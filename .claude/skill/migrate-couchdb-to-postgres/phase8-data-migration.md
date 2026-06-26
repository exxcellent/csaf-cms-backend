# Phase 8 — Data Migration Script (Optional)

This phase is optional. Ask the user whether they have existing CouchDB data that needs to be migrated before implementing.

## Purpose

Create a one-time migration utility that reads all documents from CouchDB and writes them to the new PostgreSQL database.

## Implementation

Create `src/main/java/de/bsi/secvisogram/csaf_cms_backend/migration/CouchDbMigrationRunner.java`:

```java
@Component
@Profile("migrate-couchdb")
public class CouchDbMigrationRunner implements CommandLineRunner {

    // 1. Connect to CouchDB using the old Cloudant SDK configuration
    // 2. Read all documents via _all_docs?include_docs=true
    // 3. Group by ObjectType (type field)
    // 4. For each document type:
    //    - Parse the CouchDB JSON document
    //    - Map to the corresponding JPA entity
    //    - Save via Spring Data repositories
    // 5. Report counts and any errors
    // 6. Exit after migration completes
}
```

## Migration Mapping

| CouchDB ObjectType | Target Entity | Special handling |
|---|---|---|
| `Advisory` | `AdvisoryEntity` | Extract metadata fields from JSON, keep `csaf` subtree as JSONB |
| `AdvisoryVersion` | `AdvisoryVersionEntity` | Map `advisoryReference` → `advisoryId` FK |
| `AuditTrailDocument` | `AuditTrailDocumentEntity` | Map `advisoryId` string → UUID FK |
| `AuditTrailWorkflow` | `AuditTrailWorkflowEntity` | Map `advisoryId` string → UUID FK |
| `Comment` | `CommentEntity` | Map `advisoryId` and `answerTo` strings → UUID FKs |
| `CommentAuditTrail` | `AuditTrailCommentEntity` | Map `commentId` string → UUID FK |
| `Counter` | `CounterEntity` | Direct mapping (id + count) |

## ID Handling

CouchDB uses string UUIDs (without hyphens). PostgreSQL uses standard UUID format. The migration must:
- Parse CouchDB `_id` strings into `java.util.UUID`
- Maintain referential integrity (advisory → audit trail, comment → answer_to)

## Running the Migration

```bash
# Start with the migration profile active
java -jar csaf-cms-backend.jar --spring.profiles.active=migrate-couchdb \
    --csaf.couchdb.host=old-couchdb-host \
    --csaf.couchdb.port=5984 \
    --csaf.couchdb.dbname=csaf \
    --csaf.couchdb.user=admin \
    --csaf.couchdb.password=admin
```

## Configuration

Add migration-specific properties in `application-migrate-couchdb.properties`:

```properties
# Old CouchDB connection (only used during migration)
csaf.migration.couchdb.host=${CSAF_COUCHDB_HOST:localhost}
csaf.migration.couchdb.port=${CSAF_COUCHDB_PORT:5984}
csaf.migration.couchdb.dbname=${CSAF_COUCHDB_DBNAME:csaf}
csaf.migration.couchdb.user=${CSAF_COUCHDB_USER:admin}
csaf.migration.couchdb.password=${CSAF_COUCHDB_PASSWORD:admin}
```

## Verification

- Run the migration against a test CouchDB instance with known data
- Verify document counts match between source and target
- Spot-check a few documents for data integrity
- Verify all foreign key relationships are correct