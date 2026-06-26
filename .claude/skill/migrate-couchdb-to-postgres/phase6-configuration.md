# Phase 6 — Configuration

Update all configuration files to switch from CouchDB to PostgreSQL.

## Update `application.properties`

Replace CouchDB properties with PostgreSQL:

```properties
# ===== REMOVE these CouchDB properties =====
# csaf.couchdb.host=${CSAF_COUCHDB_HOST:localhost}
# csaf.couchdb.port=${CSAF_COUCHDB_PORT:5984}
# csaf.couchdb.ssl=${CSAF_COUCHDB_SSL:false}
# csaf.couchdb.dbname=${CSAF_COUCHDB_DBNAME:csaf}
# csaf.couchdb.user=${CSAF_COUCHDB_USER:admin}
# csaf.couchdb.password=${CSAF_COUCHDB_PASSWORD:admin}

# ===== ADD these PostgreSQL properties =====
spring.datasource.url=jdbc:postgresql://${CSAF_DB_HOST:localhost}:${CSAF_DB_PORT:5432}/${CSAF_DB_NAME:csaf}
spring.datasource.username=${CSAF_DB_USER:csaf}
spring.datasource.password=${CSAF_DB_PASSWORD:csaf}

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.open-in-view=false

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

## Update `docker/compose.yaml`

Replace the CouchDB service with PostgreSQL:

```yaml
postgres:
  image: postgres:17-alpine
  hostname: postgres.csaf.internal
  environment:
    POSTGRES_DB: ${CSAF_DB_NAME:-csaf}
    POSTGRES_USER: ${CSAF_DB_USER:-csaf}
    POSTGRES_PASSWORD: ${CSAF_DB_PASSWORD:-csaf}
  ports:
    - "${CSAF_DB_PORT:-5432}:5432"
  volumes:
    - ./data/cms-db:/var/lib/postgresql/data
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U ${CSAF_DB_USER:-csaf}"]
    interval: 10s
    timeout: 5s
    retries: 5
```

Update all service references from `couchdb.csaf.internal` to `postgres.csaf.internal`.

Update the backend service's `depends_on` and environment variables accordingly.

## Update `.env` / `.env.example`

Replace CouchDB variables:

```env
# Remove:
# CSAF_COUCHDB_HOST=couchdb.csaf.internal
# CSAF_COUCHDB_PORT=5984
# CSAF_COUCHDB_DBNAME=csaf
# CSAF_COUCHDB_USER=admin
# CSAF_COUCHDB_PASSWORD=admin

# Add:
CSAF_DB_HOST=postgres.csaf.internal
CSAF_DB_PORT=5432
CSAF_DB_NAME=csaf
CSAF_DB_USER=csaf
CSAF_DB_PASSWORD=csaf
```

## Update `alpine.Dockerfile`

Check if the Dockerfile references any CouchDB-specific tooling or wait scripts. Update health check / wait-for logic to use `pg_isready` or a TCP check on port 5432.

## Verification

Run `docker compose -f docker/compose.yaml config` to validate the compose file, then:
- `./mvnw spring-boot:run` should start and connect to a local PostgreSQL instance
- Flyway should execute the migration on first startup