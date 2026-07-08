package com.bottlevault.support

import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Base class for integration tests that boot the Spring context.
 *
 * Runs Flyway migrations and Hibernate validation against a real PostgreSQL
 * matching the prod engine/version (postgres:16-alpine, see
 * docker-compose.dev.yml) instead of H2. This exists because H2-in-Postgres-mode
 * twice failed to reproduce Postgres-specific behavior that broke production:
 * a CHAR -> bpchar vs. varchar type mismatch, and a plpgsql DO block H2 couldn't
 * parse. Combined with ddl-auto: validate in application-test.yml, an
 * entity-vs-schema drift now fails the suite in CI rather than crashing
 * SessionFactory startup at boot.
 *
 * The container is a JVM-wide singleton: started once on first class load and
 * shared across every test class (and every Spring context) for the run.
 * Testcontainers' Ryuk sidecar reaps it when the JVM exits, so it is never
 * explicitly stopped.
 */
@ActiveProfiles("test")
abstract class AbstractPostgresIntegrationTest {

    companion object {
        @JvmStatic
        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:16-alpine").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName)
        }
    }
}
