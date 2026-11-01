#!/usr/bin/env bash
# Prints the sing-box-for-android commit released as app version $3 on branch $2 of the clone $1:
# the last commit before the next "Bump version", or the branch head if it's still that version.
# Exits 1 if the app never had that version (it sometimes skips a sing-box pre-release).
set -euo pipefail
R=$1 B=$2 V=$3 NEXT=""
for c in $(git -C "$R" log "origin/$B" --format=%H -G'^VERSION_NAME=' -- version.properties); do
  if [ "$(git -C "$R" show "$c:version.properties" | sed -n 's/^VERSION_NAME=//p')" = "$V" ]; then
    if [ -n "$NEXT" ]; then git -C "$R" rev-parse "$NEXT^"; else git -C "$R" rev-parse "origin/$B"; fi
    exit 0
  fi
  NEXT=$c
done
exit 1
