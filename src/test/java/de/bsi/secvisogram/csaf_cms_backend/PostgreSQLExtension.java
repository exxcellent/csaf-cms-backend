package de.bsi.secvisogram.csaf_cms_backend;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Test extension that starts a PostgreSQL container and sets the corresponding Spring datasource
 * properties. The container is shared across all tests in the same JVM (static) and cleaned up
 * on JVM shutdown. Flyway runs automatically on Spring context startup (creates the schema).
 *
 * <p>Usage: {@code @ExtendWith(PostgreSQLExtension.class)} on integration test classes.</p>
 */
public class PostgreSQLExtension implements BeforeAllCallback, AfterAllCallback {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("csaf_test")
                    .withUsername("test")
                    .withPassword("test");

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        System.setProperty("spring.datasource.url", POSTGRES.getJdbcUrl());
        System.setProperty("spring.datasource.username", POSTGRES.getUsername());
        System.setProperty("spring.datasource.password", POSTGRES.getPassword());
        // Enable JPA and Flyway for integration tests (overrides test application.properties exclusion)
        System.setProperty("spring.autoconfigure.exclude", "");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        System.setProperty("spring.flyway.enabled", "true");
        System.setProperty("spring.flyway.clean-disabled", "false");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Container is shared (static), stopped by JVM shutdown hook
    }
}
