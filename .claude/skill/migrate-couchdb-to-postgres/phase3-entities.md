# Phase 3 — JPA Entities

Create JPA entity classes in a new package `src/main/java/de/bsi/secvisogram/csaf_cms_backend/entity/`.

## Checkstyle Import Rules (CRITICAL)

The project enforces `CustomImportOrder` with `STATIC###THIRD_PARTY_PACKAGE`. All non-static imports must be in a **single sorted group** with NO blank lines between them. Third-party (`com.*`, `jakarta.*`, `org.*`) naturally sorts before `java.*`:

```java
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
```

Each annotation must be on its **own line** (no `@Id @GeneratedValue` on same line).

## Entity Mapping Rules

- Every entity gets `@Entity`, `@Table(name = "...")`.
- UUID primary keys: `@Id` with `@GeneratedValue(strategy = GenerationType.UUID)`.
- The `csaf` JSONB column: use `@JdbcTypeCode(SqlTypes.JSON)` from Hibernate 6, typed as `com.fasterxml.jackson.databind.JsonNode`.
- The `diff` JSONB column: same approach.
- Optimistic locking on `AdvisoryEntity`: `@Version private Long version;` (replaces CouchDB `_rev`).
- The `Counter` entity uses `@Id String id` (not UUID) since counter IDs are well-known strings.
- Use `@Column(name = "\"user\"")` for the `user` column (reserved word in PostgreSQL).

## Entities to Create

### AdvisoryEntity (`advisories` table)

```java
@Entity
@Table(name = "advisories")
public class AdvisoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_state", nullable = false)
    private String workflowState = "Draft";

    @Column(nullable = false)
    private String owner;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode csaf;

    @Column(name = "versioning_type", nullable = false)
    private String versioningType = "Semantic";

    @Column(name = "last_major_version")
    private String lastMajorVersion;

    @Column(name = "tmp_tracking_id")
    private String tmpTrackingId;

    @Column(name = "advisory_reference")
    private UUID advisoryReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;
}
```

### AdvisoryVersionEntity (`advisory_versions` table)

- `@ManyToOne` → `AdvisoryEntity` (advisoryId FK)
- Fields: workflowState, owner, csaf (JSONB), versioningType, lastMajorVersion, createdAt

### AuditTrailDocumentEntity (`audit_trail_documents` table)

- `@ManyToOne` → `AdvisoryEntity` (advisoryId FK)
- Fields: createdAt, user, changeType, diff (JSONB), oldDocVersion, docVersion

### AuditTrailWorkflowEntity (`audit_trail_workflows` table)

- `@ManyToOne` → `AdvisoryEntity` (advisoryId FK)
- Fields: createdAt, user, changeType, oldState, newState, oldDocVersion, docVersion

### CommentEntity (`comments` table)

- `@ManyToOne` → `AdvisoryEntity` (advisoryId FK)
- Self-referencing `@ManyToOne` for `answerTo`
- Fields: owner, commentText, csafNodeId, fieldName, answerTo, createdAt

### AuditTrailCommentEntity (`audit_trail_comments` table)

- `@ManyToOne` → `CommentEntity` (commentId FK)
- Fields: createdAt, user, changeType, commentText

### CounterEntity (`counters` table)

- `@Id String id` (not UUID, no generation strategy)
- Fields: count (Long)

## Design Rules

- **Do NOT** use bidirectional relationships (`@OneToMany` on the parent side) — the current code never navigates that direction.
- Keep it simple with `@ManyToOne` + `@JoinColumn` only.
- Use `FetchType.LAZY` on all `@ManyToOne` relationships.

## Verification

Run `./mvnw compile` to ensure all entities compile. Spring Boot will validate entity mappings against the Flyway-created schema on startup (since `ddl-auto=validate`).