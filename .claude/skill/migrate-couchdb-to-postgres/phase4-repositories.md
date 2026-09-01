# Phase 4 — Spring Data Repositories

Create Spring Data JPA repository interfaces in `src/main/java/de/bsi/secvisogram/csaf_cms_backend/repository/`.

## Repositories to Create

### AdvisoryRepository

```java
public interface AdvisoryRepository extends JpaRepository<AdvisoryEntity, UUID>,
                                            JpaSpecificationExecutor<AdvisoryEntity> {

    List<AdvisoryEntity> findByWorkflowState(String workflowState);

    // Tracking ID lookup (inside JSONB)
    @Query(value = "SELECT * FROM advisories WHERE csaf->'document'->'tracking'->>'id' = :trackingId",
           nativeQuery = true)
    Optional<AdvisoryEntity> findByTrackingId(@Param("trackingId") String trackingId);
}
```

### AdvisoryVersionRepository

```java
public interface AdvisoryVersionRepository extends JpaRepository<AdvisoryVersionEntity, UUID> {
    List<AdvisoryVersionEntity> findByAdvisoryId(UUID advisoryId);
    void deleteByAdvisoryId(UUID advisoryId);
}
```

### AuditTrailDocumentRepository

```java
public interface AuditTrailDocumentRepository extends JpaRepository<AuditTrailDocumentEntity, UUID> {
    List<AuditTrailDocumentEntity> findByAdvisoryId(UUID advisoryId);
    void deleteByAdvisoryId(UUID advisoryId);
}
```

### AuditTrailWorkflowRepository

```java
public interface AuditTrailWorkflowRepository extends JpaRepository<AuditTrailWorkflowEntity, UUID> {
    List<AuditTrailWorkflowEntity> findByAdvisoryId(UUID advisoryId);
    void deleteByAdvisoryId(UUID advisoryId);
}
```

### CommentRepository

```java
public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {
    List<CommentEntity> findByAdvisoryId(UUID advisoryId);
    List<CommentEntity> findByAnswerTo(UUID commentId);
    void deleteByAdvisoryId(UUID advisoryId);
}
```

### AuditTrailCommentRepository

```java
public interface AuditTrailCommentRepository extends JpaRepository<AuditTrailCommentEntity, UUID> {
    List<AuditTrailCommentEntity> findByCommentId(UUID commentId);
    void deleteByCommentIdIn(Collection<UUID> commentIds);
}
```

### CounterRepository

```java
public interface CounterRepository extends JpaRepository<CounterEntity, String> {

    // Atomic increment — eliminates the race condition in the CouchDB implementation
    @Modifying
    @Query(value = "UPDATE counters SET count = count + 1 WHERE id = :id RETURNING count",
           nativeQuery = true)
    Long incrementAndGet(@Param("id") String id);
}
```

## JSONB Query Strategy

The current `CouchDBFilterCreator` builds Mango selectors for advisory search/filter. Replace with:

1. **PostgreSQL JSONB operators** in `@Query` native queries: `jsonb_path_exists()`, `@>`, `->>`, `#>>`.
2. For the complex filter expression tree (`Expression` → Mango), create a **`PostgresFilterCreator`** that generates a JPA `Specification<AdvisoryEntity>` or a native SQL `WHERE` clause fragment.
3. **Recommended approach:** Convert `CouchDBFilterCreator` to build JPA `Specification<AdvisoryEntity>` using the Criteria API with Hibernate's JSONB functions.

### Key JSONB query patterns to implement

| Use case | Native SQL pattern |
|---|---|
| Find by tracking ID | `csaf->'document'->'tracking'->>'id' = :id` |
| Filter by TLP label | `csaf->'document'->'distribution'->'tlp'->>'label' = :label` |
| Filter by document title (contains) | `csaf->'document'->>'title' ILIKE '%' \|\| :title \|\| '%'` |
| Release date comparison | `(csaf->'document'->'tracking'->>'current_release_date')::timestamptz < now()` |
| Array element search | `jsonb_path_exists(csaf, '$.vulnerabilities[*] ? (@.cve == $cve)', jsonb_build_object('cve', :cve))` |

## Verification

Run `./mvnw compile` to ensure all repository interfaces compile correctly.