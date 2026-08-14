// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.cfg.DateTimeFeature

@Configuration
class JacksonConfig {

    // Jackson 3 (Spring Boot 4) auto-registers the Kotlin and Java-time modules from
    // the classpath, so this customizer only needs to enforce ISO-8601 date output.
    // In Jackson 3, WRITE_DATES_AS_TIMESTAMPS moved from SerializationFeature to
    // DateTimeFeature (a DatatypeFeature).
    @Bean
    fun jsonCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
}
