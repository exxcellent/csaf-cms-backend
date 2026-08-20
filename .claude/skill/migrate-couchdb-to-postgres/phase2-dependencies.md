# Phase 2 — Dependencies

Update `pom.xml` to add PostgreSQL/JPA dependencies and remove the CouchDB SDK.

## Add

```xml
<!-- PostgreSQL + JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- Flyway for schema migrations -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<!-- Hypersistence for JSONB mapping -->
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.9.0</version>
</dependency>
<!-- Testcontainers PostgreSQL (test scope) — MUST specify version explicitly -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>
```

> **⚠️ Gotcha:** The Spring Boot 4.1.0 BOM manages `testcontainers-junit-jupiter` but NOT `testcontainers:postgresql`. Use `${testcontainers.version}` (from the parent BOM) explicitly, otherwise Maven will fail with "version is missing".

## Remove

```xml
<!-- Remove IBM Cloudant SDK -->
<dependency>
    <groupId>com.ibm.cloud</groupId>
    <artifactId>cloudant</artifactId>
</dependency>
```

## Important Notes

- Check the current Spring Boot version in `pom.xml` to select the correct Hypersistence Utils version (`hibernate-63` for Hibernate 6.3+/Spring Boot 3.2+).
- Look up the latest compatible versions using context7 or web search.
- Do NOT remove the Cloudant SDK yet if Phase 5 (service rewrite) hasn't been completed — the code still references it. Instead, add the new dependencies first and remove Cloudant only after Phase 5 is done.
- Hypersistence Utils turned out to be unnecessary — Hibernate 6's built-in `@JdbcTypeCode(SqlTypes.JSON)` handles JSONB mapping directly. Skip this dependency unless you need advanced JSONB features.

## Post-dependency fix: Disable JPA auto-configuration in tests

Adding `spring-boot-starter-data-jpa` causes Spring Boot to auto-configure a DataSource. Existing tests will fail with `Failed to determine a suitable driver class` because no database URL is configured yet.

**Add to `src/test/resources/application.properties`:**

```properties
# Disable JPA/DataSource auto-configuration during migration (no PostgreSQL in unit tests yet)
spring.autoconfigure.exclude=org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,\
  org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,\
  org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
```

This is temporary — remove it in Phase 7 when proper PostgreSQL Testcontainers support is added.

## Verification

Run `./mvnw compile` to ensure all dependencies resolve correctly.
Run `./mvnw verify -Pgithub-action` to ensure existing tests still pass (306 tests, 0 failures).