// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
plugins {
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
}

group = "com.bottlevault"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    // Boot 4 extracted DB-migration auto-config into a dedicated module: a bare
    // flyway-core dependency no longer triggers it, so use the starter (brings
    // flyway-core + spring-boot-flyway autoconfigure). Postgres support still needs
    // the flyway-database-postgresql module explicitly on Flyway 12.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // OpenAPI documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4 modularized the test starters: spring-boot-starter-test no longer
    // bundles the MockMvc slice (@AutoConfigureMockMvc / @WebMvcTest). Add it explicitly.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Integration tests run Flyway against a real PostgreSQL via Testcontainers,
    // matching the prod engine/version (postgres:16-alpine). Version is managed
    // by the Spring Boot dependency BOM.
    testImplementation("org.testcontainers:testcontainers-postgresql")

    // H2 for local dev without PostgreSQL (application-dev profile only)
    runtimeOnly("com.h2database:h2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Testcontainers locates the Docker daemon via a forked test worker, which
    // does NOT inherit an interactive shell's DOCKER_HOST. On Windows, Docker
    // Desktop's legacy //./pipe/docker_engine is a stub proxy that answers the
    // daemon ping with HTTP 400, so Testcontainers' default npipe probe fails;
    // the real Linux engine lives on //./pipe/dockerDesktopLinuxEngine (the
    // active `desktop-linux` context). Point the worker at it explicitly. On
    // Linux/macOS (incl. CI) the default unix-socket probe works, so leave the
    // environment untouched and let Testcontainers auto-detect.
    if (org.gradle.internal.os.OperatingSystem.current().isWindows
        && System.getenv("DOCKER_HOST") == null
    ) {
        environment("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine")
    }

    // Docker Engine 29 enforces a minimum API version of 1.40, but the
    // docker-java client bundled with Testcontainers defaults to an older
    // version, so the daemon rejects the connection ping with HTTP 400.
    // docker-java reads the target version from the `api.version` system
    // property; pin it to 1.40 — the floor Engine 29 requires and still within
    // range for every daemon back to 19.03, so it doesn't break older Docker.
    // Harmless on engines that already negotiate a compatible version.
    systemProperty("api.version", "1.40")
}
