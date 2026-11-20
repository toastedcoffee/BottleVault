# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# The API's egress proxy: Alpine's Squid, built with OpenSSL, so it can read the
# TLS server name (SNI) of every tunnel without decrypting anything. The config
# itself is not baked in: both compose files inject it (configs: squid_conf), so
# it stays readable next to the rest of the stack.
#
# Build from the repo root, like the other images:
#   docker build -f egress/Dockerfile .
FROM alpine:3.24@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf
# Squid is GPL-2.0-or-later, and so are other packages in this image (busybox
# is GPL-2.0-only). CI publishes their complete corresponding source next to
# every egress image it publishes; /usr/share/doc/bottlevault/SOURCE says where.
LABEL org.opencontainers.image.licenses="GPL-2.0-or-later"
LABEL org.opencontainers.image.source="https://github.com/toastedcoffee/BottleVault"
LABEL org.opencontainers.image.title="BottleVault egress proxy"

# openssl: the start script makes a throwaway certificate with it (see start.sh).
RUN apk add --no-cache squid openssl

# Our own three scripts are AGPL-3.0-only, so this image carries that licence
# text as well, exactly as the api and web images do.
COPY LICENSE COPYRIGHT TRADEMARKS.md egress/SOURCE /usr/share/doc/bottlevault/
COPY --chmod=0755 egress/start.sh /usr/local/bin/egress-start
COPY --chmod=0755 egress/sni-check.sh /usr/local/bin/sni-check
COPY --chmod=0755 egress/healthcheck.sh /usr/local/bin/egress-healthcheck

# Alpine's unprivileged "squid" user.
USER 31:31
ENTRYPOINT ["/usr/local/bin/egress-start"]
