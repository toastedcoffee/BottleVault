#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# Egress healthcheck. Each probe sends a CONNECT line and nothing after it, so no
# probe ever leaves the container, and both target names below are excluded
# from the access log (squid_conf: selftest_probe, selftest_ip_probe).

# 1. Port 3128 hands every allowed tunnel to the TLS check on 127.0.0.1:3129.
#    Squid marks that hop down at every start (it tests it a few milliseconds
#    before 3129 listens) and only re-tests it when a request needs it, so the
#    first real request after a start would fail. This probe is that request:
#    a 200 means the hop is up; anything else re-tests it for the next run.
r=$(printf 'CONNECT egress-selftest.invalid:443 HTTP/1.1\r\nHost: egress-selftest.invalid:443\r\n\r\n' |
  nc -w 2 127.0.0.1 3128 | head -n 1)
case "$r" in "HTTP/1.1 200 "*) ;; *) echo "front port: $r"; exit 1 ;; esac

# 2. The TLS check is live. On a port where ssl_bump is active, Squid answers
#    even a refused CONNECT with 200 (it would show its error inside TLS). If TLS
#    setup failed, Squid runs that port as a plain one instead, which answers 403
#    here - and would pass every tunnel through unchecked.
r=$(printf 'CONNECT 192.0.2.1:443 HTTP/1.1\r\nHost: 192.0.2.1:443\r\n\r\n' |
  nc -w 2 127.0.0.1 3129 | head -n 1)
case "$r" in "HTTP/1.1 200 "*) ;; *) echo "TLS check not active: $r"; exit 1 ;; esac
