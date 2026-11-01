#!/usr/bin/env bash
# Export BitProxy commits as patch files (one folder per repo), based on versions.env.
# Re-apply on a new release:  git switch -c bitproxy <new-base> && git am -3 patches/android/*.patch
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
source versions.env
rm -rf patches && mkdir -p patches/core patches/android
git -C sing-box format-patch -q "$SING_BOX_TAG..bitproxy" -o "$PWD/patches/core"
git -C sing-box/clients/android format-patch -q "$SFA_COMMIT..bitproxy" -o "$PWD/patches/android"
# sing-tun: based on the version sing-box's go.mod requires (see prepare-sing-tun.sh).
if [ -d sing-tun ]; then
  mkdir -p patches/sing-tun
  V=$(git -C sing-box show "$SING_BOX_TAG:go.mod" | sed -n 's/^\s*github.com\/sagernet\/sing-tun \(v[^ ]*\).*/\1/p' | head -1)
  BASE=$(echo "$V" | grep -oE '[0-9a-f]{12}$' || echo "$V")
  git -C sing-tun format-patch -q "$BASE..bitproxy" -o "$PWD/patches/sing-tun"
fi
ls patches/*
