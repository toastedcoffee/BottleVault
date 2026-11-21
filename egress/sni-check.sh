#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# Squid external ACL helper (external_acl_type sni_is_connect_host in the
# compose files' squid_conf). Squid sends one line per tunnel:
#   <TLS SNI, or "-" if the ClientHello had none> <CONNECT host> <%DATA, "-">
# It %-encodes each field, so none contains a space, and appends %DATA itself.
# Answer OK only when the SNI names exactly the host the CONNECT line named
# (ignoring case), ERR otherwise - including when there is no SNI at all, which
# Squid's own ssl::server_name ACL cannot tell apart from a matching one.
#
# Run as a file with this #! line, not as "/bin/sh <file>": Squid starts helpers
# with argv[0] set to "(sh)", which busybox does not recognise.
while read -r sni host _; do
  if [ "$sni" != "-" ] && [ -n "$host" ] &&
     [ "$(printf '%s' "$sni" | tr 'A-Z' 'a-z')" = "$(printf '%s' "$host" | tr 'A-Z' 'a-z')" ]; then
    echo OK
  else
    echo ERR
  fi
done
