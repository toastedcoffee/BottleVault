#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# Entrypoint of the egress image. Squid refuses to turn on its TLS check
# (ssl_bump) for a port that has no certificate, and then quietly runs that port
# as a plain one, so every tunnel would pass unchecked. The proxy never presents
# this certificate: every tunnel is either passed through untouched or closed,
# never decrypted. So a fresh, self-signed, throwaway one is made at each start,
# in tmpfs. It is trusted by nothing and gone when the container stops.
set -eu
umask 077
mkdir -p /tmp/tls
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
  -subj /CN=bottlevault-egress-splice-only \
  -keyout /tmp/tls/splice.key -out /tmp/tls/splice.pem 2>/dev/null
exec squid -N -f /etc/squid/squid.conf
