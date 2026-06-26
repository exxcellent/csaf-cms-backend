# Phase 7 — Tests

Replace the CouchDB test infrastructure with PostgreSQL Testcontainers.

## Replace CouchDBExtension with PostgreSQL Testcontainers

Create `src/test/java/.../PostgreSQLExtension.java` (JUnit 5 Extension):

```java
public class PostgreSQLExtension implements BeforeAllCallback, AfterAllCallback {

    private static final PostgreSQLContainer<?> container =
        new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("csaf_test")
            .withUsername("test")
            .withPassword("test");

    @Override
    public void beforeAll(ExtensionContext context) {
        container.start();
        System.setProperty("spring.datasource.url", container.getJdbcUrl());
        System.setProperty("spring.datasource.username", container.getUsername());
        System.setProperty("spring.datasource.password", container.getPassword());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Container is shared across tests via static field — stopped by JVM shutdown hook
    }
}
```

### Key differences from CouchDBExtension

- No per-test database creation/deletion needed — use `@Transactional` + automatic rollback instead
- Flyway runs automatically on Spring context startup (creates schema)
- Container is started once and reused across all test classes (faster)

## Update Test Classes

### Tests to delete or heavily rewrite

- `CouchDbServiceTest` → delete (the repository layer is now standard Spring Data; test via integration tests)

### Tests to update (switch extension)

- `AdvisoryWorkflowSemanticVersioningTest` — replace `@ExtendWith(CouchDBExtension.class)` with `@ExtendWith(PostgreSQLExtension.class)`
- `AdvisorySearchUtilTest` — rewrite for `Specification`-based filtering
- Any other test class using `CouchDBExtension`

### Tests that should need minimal changes

- `AdvisoryControllerTest` (MockMvc with `@WebMvcTest`) — mocks at the service level, should work as-is
- Unit tests for `AdvisoryWorkflowUtil`, versioning, etc. — no DB dependency

### New integration tests to add

- `AdvisoryRepositoryTest` — test JSONB queries, Specification-based filtering
- `CounterRepositoryTest` — test atomic increment

## CI Profile

Update the `github-action` Maven profile exclusions:

- **Remove** exclusions for `CouchDbServiceTest`, `AdvisoryWorkflowSemanticVersioningTest`, `AdvisorySearchUtilTest` (these no longer require an external CouchDB instance)
- PostgreSQL Testcontainers works in CI (GitHub Actions has Docker available)
- Consider whether any tests still need exclusion

## Test Configuration

Create `src/test/resources/application-test.properties` if needed:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.clean-disabled=false
```

## Verification

```bash
./mvnw test                          # all tests pass
./mvnw verify                        # tests + JaCoCo + SpotBugs + Checkstyle
./mvnw -Pgithub-action clean verify  # CI profile works
```