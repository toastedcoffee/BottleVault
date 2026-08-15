# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
FROM node:26-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
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
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
