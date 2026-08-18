# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
FROM gradle:9.6.1-jdk21-alpine AS build
WORKDIR /app
# Build context is the repo root: docker build -f backend/Dockerfile .
COPY backend/build.gradle.kts backend/settings.gradle.kts backend/gradle.properties ./
COPY backend/gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY backend/src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.licenses="AGPL-3.0-only"
LABEL org.opencontainers.image.source="https://github.com/toastedcoffee/BottleVault"
LABEL org.opencontainers.image.title="BottleVault"
WORKDIR /app
# AGPL sections 4/5: distributing this image is conveyance, so recipients must
# receive the licence text alongside the binary. Do not remove.
COPY LICENSE COPYRIGHT TRADEMARKS.md /usr/share/doc/bottlevault/
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
