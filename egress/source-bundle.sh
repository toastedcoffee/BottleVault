#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-only
# SPDX-FileCopyrightText: 2025-2026 toastedcoffee
#
# source-bundle.sh INSTALLED_DB OUT_DIR
#
# Collects the complete corresponding source of every Alpine package in the
# egress image: Squid is GPL-2.0-or-later and busybox GPL-2.0-only, so whoever
# publishes the image must also make its source available from the same place.
# CI publishes OUT_DIR as ghcr.io/toastedcoffee/bottlevault-egress:<tag>-source
# before it pushes the image itself.
#
# INSTALLED_DB is the image's /lib/apk/db/installed. For each package origin it
# names, this fetches Alpine's build recipe (APKBUILD, patches, scripts) at the
# exact aports commit the package was built from, then every source file that
# recipe lists, checked against the recipe's checksums:
#   OUT_DIR/aports/<repo>/<origin>/   the recipe directory
#   OUT_DIR/distfiles/                the upstream source archives
#   OUT_DIR/PACKAGES.tsv              package, version, origin, commit, licence
# Exits non-zero, naming the origin, if any recipe or source cannot be fetched
# or fails its checksum. Run it as root in the image's own base, alpine:3.24.
set -u
db=$1 out=$2
[ -r "$db" ] || { echo "cannot read $db" >&2; exit 2; }
apk add --no-cache -q abuild curl >/dev/null || { echo "apk add abuild curl failed" >&2; exit 2; }
mkdir -p "$out/aports" "$out/distfiles"
aports=https://gitlab.alpinelinux.org/alpine/aports/-/archive
# abuild tries this mirror of every source Alpine has built before the upstream URL.
export DISTFILES_MIRROR=https://distfiles.alpinelinux.org/distfiles/v3.24
export SRCDEST=$out/distfiles

# One record per package: name, version, origin, aports commit, licence.
printf 'package\tversion\torigin\taports_commit\tlicense\n' > "$out/PACKAGES.tsv"
awk -F: '
  /^P:/ { p = substr($0, 3) } /^V:/ { v = substr($0, 3) } /^o:/ { o = substr($0, 3) }
  /^c:/ { c = substr($0, 3) } /^L:/ { l = substr($0, 3) }
  /^$/  { if (p != "") printf "%s\t%s\t%s\t%s\t%s\n", p, v, o, c, l; p = v = o = c = l = "" }
  END   { if (p != "") printf "%s\t%s\t%s\t%s\t%s\n", p, v, o, c, l }
' "$db" >> "$out/PACKAGES.tsv"

failed=0
tmp=$(mktemp -d)
# Each origin once, at the commit its packages were built from.
for oc in $(tail -n +2 "$out/PACKAGES.tsv" | awk -F'\t' '{ print $3 "@" $4 }' | sort -u); do
  origin=${oc%@*} commit=${oc#*@}
  if [ -z "$origin" ] || [ -z "$commit" ]; then
    echo "FAIL $oc: package record has no origin or aports commit" >&2; failed=1; continue
  fi
  found=
  for repo in main community; do
    rm -rf "$tmp"/*
    if curl -fsSL --retry 3 -o "$tmp/a.tgz" "$aports/$commit/aports-$commit.tar.gz?path=$repo/$origin" 2>/dev/null &&
       tar -xzf "$tmp/a.tgz" -C "$tmp" 2>/dev/null &&
       [ -f "$tmp/aports-$commit-$repo-$origin/$repo/$origin/APKBUILD" ]; then
      mkdir -p "$out/aports/$repo"
      rm -rf "$out/aports/$repo/$origin"
      mv "$tmp/aports-$commit-$repo-$origin/$repo/$origin" "$out/aports/$repo/$origin"
      found=$repo; break
    fi
  done
  if [ -z "$found" ]; then
    echo "FAIL $origin: no APKBUILD at aports commit $commit (main or community)" >&2; failed=1; continue
  fi
  # abuild links each source into a src/ work directory with absolute symlinks;
  # those are not source (the files are in distfiles/) and an image build refuses
  # them, so drop it once the checksums are verified.
  # Some recipes also link one file to another (squid.pre-upgrade ->
  # squid.pre-install); store those as plain copies so the bundle holds only
  # ordinary files wherever it is unpacked.
  if (cd "$out/aports/$found/$origin" && abuild -F fetch >/dev/null 2>&1 && abuild -F verify >/dev/null 2>&1 &&
      rm -rf src &&
      find . -type l | while read -r l; do t=$(readlink -f "$l") && rm "$l" && cp "$t" "$l" || exit 1; done); then
    echo "ok   $found/$origin @ $commit"
  else
    echo "FAIL $origin: sources could not be fetched or failed their checksums" >&2; failed=1
  fi
done
rm -rf "$tmp"
exit $failed
