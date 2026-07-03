plugins {
    id("org.springframework.boot") version "3.4.13"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    kotlin("plugin.jpa") version "2.4.0"
}

group = "com.bottlevault"
version = "0.1.0"

// CVE overrides on top of the Spring Boot 3.4.13 BOM. Each pins a transitive
// dependency ABOVE the version the BOM manages, to close a confirmed, in-range
// CVE. Verified against `./gradlew dependencies` (2026-07-01). Drop a pin once
// the BOM manages a version at or above it.
//   - spring-security: BOM ships 6.4.13. Covers CVE-2025-41248 (auth bypass,
//     fixed 6.4.11) already, but CVE-2026-22732 (CVSS 8.6 security-header drop)
//     has NO OSS fix on the 6.4.x line — 6.4.15 is Enterprise-only. 6.5.9 is the
//     OSS fix. 6.5.x rides Spring Framework 6.2.x (BOM ships 6.2.15), so it is
//     compatible with this Boot 3.4 line.
//   - spring-framework: BOM ships 6.2.15, which already fixes CVE-2025-41249
//     (>= 6.2.11). Pinned to 6.2.17 anyway so Security 6.5.9 runs on the exact
//     Framework it was built and tested against (6.5.9 requests 6.2.17, which the
//     BOM would otherwise force back down to 6.2.15) — makes the grafted security
//     stack a coherent, Spring-tested pairing. 6.2.17 is a safe same-minor patch.
//   - postgresql: BOM ships 42.7.8. CVE-2026-42198 (SCRAM-auth DoS) fixed 42.7.11.
//   - commons-lang3: BOM caps to 3.17.0, which is vulnerable to CVE-2025-48924
//     (recursion DoS). springdoc transitively requests 3.20.0; let it resolve.
extra["spring-security.version"] = "6.5.9"
extra["spring-framework.version"] = "6.2.17"
extra["postgresql.version"] = "42.7.11"
extra["commons-lang3.version"] = "3.20.0"

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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // OpenAPI documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Integration tests run Flyway against a real PostgreSQL via Testcontainers,
    // matching the prod engine/version (postgres:16-alpine). Version is managed
    // by the Spring Boot dependency BOM.
    testImplementation("org.testcontainers:postgresql")

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
