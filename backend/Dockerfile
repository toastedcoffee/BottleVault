# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
FROM gradle:9.8.0-jdk21-alpine AS build
WORKDIR /app
# Build context is the repo root: docker build -f backend/Dockerfile .
COPY backend/build.gradle.kts backend/settings.gradle.kts backend/gradle.properties ./
COPY backend/gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY backend/src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:24-jre-alpine
LABEL org.opencontainers.image.licenses="AGPL-3.0-only"
LABEL org.opencontainers.image.source="https://github.com/toastedcoffee/BottleVault"
LABEL org.opencontainers.image.title="BottleVault"
WORKDIR /app
# AGPL section 6 (object code, conveyed under the terms of sections 4 and 5):
# publishing this image is conveyance, so recipients must receive the licence
# text with the binary. Section 6 also requires the Corresponding Source — the
# org.opencontainers.image.source label above is section 6(d)'s clear directions
# to it. Both halves are load-bearing: do not remove either.
COPY LICENSE COPYRIGHT TRADEMARKS.md /usr/share/doc/bottlevault/
COPY --from=build /app/build/libs/*.jar app.jar
# Never run as root. 568:568 is TrueNAS's conventional "apps" user. The data
# directories are created here and owned by it, so a *named* volume mounted over
# them starts out writable. A bind mount keeps the host directory's owner — see
# the chown step in docker-compose.prod.yml's header.
RUN addgroup -S -g 568 apps \
 && adduser -S -D -H -u 568 -G apps -s /sbin/nologin bottlevault \
 && mkdir -p /data/uploads /data/logs \
 && chown -R 568:568 /data
USER 568:568
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
