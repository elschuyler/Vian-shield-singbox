#!/usr/bin/env bash
# Build sing-box against a patched sing-tun: clone the sing-tun version that sing-box's go.mod
# requires into $2/sing-tun, apply patches/sing-tun/*.patch and point go.mod at it (replace).
# Usage: prepare-sing-tun.sh <sing-box dir> <work dir>. Does nothing without patches.
set -euo pipefail
ROOT=$(cd "$(dirname "$(readlink -f "$0")")" && pwd)
SB=$(cd "$1" && pwd); W=$(cd "$2" && pwd)
compgen -G "$ROOT/patches/sing-tun/*.patch" >/dev/null || exit 0
V=$(sed -n 's/^\s*github.com\/sagernet\/sing-tun \(v[^ ]*\).*/\1/p' "$SB/go.mod" | head -1)
[ -n "$V" ] || { echo "sing-tun not found in $SB/go.mod" >&2; exit 1; }
# Pseudo-versions end in a 12-char commit hash; otherwise V is a tag.
REF=$(echo "$V" | grep -oE '[0-9a-f]{12}$' || echo "$V")
rm -rf "$W/sing-tun"
git clone -q https://github.com/SagerNet/sing-tun.git "$W/sing-tun"
git -C "$W/sing-tun" checkout -q "$REF"
git -C "$W/sing-tun" -c user.name=bitproxy -c user.email=bitproxy@localhost am -q -3 "$ROOT"/patches/sing-tun/*.patch
(cd "$SB" && go mod edit -replace "github.com/sagernet/sing-tun=$W/sing-tun")
echo "sing-tun $V patched"
