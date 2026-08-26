# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
FROM node:26-alpine AS build
WORKDIR /app
# Build context is the repo root: docker build -f frontend/Dockerfile .
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci
COPY frontend/ ./
# AGPL section 13: bake the running commit into the bundle so the in-app source
# link points at the exact tree this image was built from. Declared here rather
# than before `npm ci` so a new commit does not invalidate the dependency layer
# cache. Defaults to 'dev' so a build without the arg still succeeds.
ARG GIT_SHA=dev
ENV VITE_GIT_SHA=$GIT_SHA
RUN npm run build

FROM nginx:alpine
LABEL org.opencontainers.image.licenses="AGPL-3.0-only"
LABEL org.opencontainers.image.source="https://github.com/toastedcoffee/BottleVault"
LABEL org.opencontainers.image.title="BottleVault"
# AGPL section 6 (object code, conveyed under the terms of sections 4 and 5):
# publishing this image is conveyance, so recipients must receive the licence
# text with the binary. Section 6 also requires the Corresponding Source — the
# org.opencontainers.image.source label above is section 6(d)'s clear directions
# to it. Both halves are load-bearing: do not remove either.
COPY LICENSE COPYRIGHT TRADEMARKS.md /usr/share/doc/bottlevault/
COPY --from=build /app/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
