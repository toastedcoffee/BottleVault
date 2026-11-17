// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import com.bottlevault.support.AbstractPostgresIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

/**
 * The global JPA query timeout must stop a runaway application query, but must
 * never reach plain JDBC work, which is how Flyway applies migrations: a long
 * migration cut off mid-deploy would leave the schema half-applied.
 *
 * The limit is lowered to 1 s here so the test runs quickly; production uses
 * the 15 s value in application.yml.
 */
@SpringBootTest(properties = ["spring.jpa.properties.jakarta.persistence.query.timeout=1000"])
class QueryTimeoutIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var entityManager: EntityManager
    @Autowired lateinit var dataSource: DataSource

    @Test
    @Transactional
    fun `an application query that runs too long is cancelled`() {
        val started = System.nanoTime()
        assertThrows(PersistenceException::class.java) {
            entityManager.createNativeQuery("SELECT pg_sleep(3)").singleResult
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs < 2_500, "should stop near the 1 s limit, took $elapsedMs ms")
    }

    @Test
    @Transactional
    fun `a JPQL query, the kind Spring Data repositories run, is cancelled too`() {
        val started = System.nanoTime()
        assertThrows(PersistenceException::class.java) {
            entityManager.createQuery(
                "select count(b) from Bottle b where function('pg_sleep', 3) is null"
            ).singleResult
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs < 2_500, "should stop near the 1 s limit, took $elapsedMs ms")
    }

    @Test
    fun `plain JDBC work, which is how Flyway runs migrations, is not limited`() {
        dataSource.connection.use { c -> c.createStatement().use { it.execute("SELECT pg_sleep(2)") } }
    }
}

/** The value the app ships with, read from the default (unoverridden) context. */
@SpringBootTest
class QueryTimeoutDefaultIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `application queries are limited to 15 seconds by default`() {
        assertEquals("15000", entityManagerFactory.properties["jakarta.persistence.query.timeout"]?.toString())
    }
}
