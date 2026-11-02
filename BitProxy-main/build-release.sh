#!/usr/bin/env bash
# Fresh BitProxy build: pinned upstream (versions.env) + patches/ -> APKs. Used by CI, runs locally too.
# Needs: Go, JDK 17, Android SDK with licenses + NDK (ANDROID_HOME), gomobile/gobind on PATH.
# Signing: set RELEASE_KEYSTORE (path) and LOCAL_PROPERTIES (KEYSTORE_PASS=, ALIAS_NAME=, ALIAS_PASS=)
# for a signed release build; without them a debug build is made.
set -euo pipefail
ROOT=$(cd "$(dirname "$(readlink -f "$0")")" && pwd)
source "$ROOT/versions.env"
# Trying a newer upstream without editing versions.env (see check-upstream.sh):
PATCH_BASE=$SFA_COMMIT # the commit patches/android was exported from
SING_BOX_TAG=${TRY_SING_BOX_REF:-$SING_BOX_TAG}
SFA_COMMIT=${TRY_SFA_REF:-$SFA_COMMIT}
W=${WORK_DIR:-$ROOT/build-work}
rm -rf "$W" && mkdir -p "$W" && cd "$W"

git clone -q --depth 1 --branch "$SING_BOX_TAG" https://github.com/SagerNet/sing-box.git sing-box
git init -q sing-box-for-android
git -C sing-box-for-android fetch -q --depth 1 https://github.com/SagerNet/sing-box-for-android.git "$SFA_COMMIT"
git -C sing-box-for-android checkout -q FETCH_HEAD
# The patch base too, so "git am -3" has the original files and can merge small upstream changes.
[ "$PATCH_BASE" = "$SFA_COMMIT" ] || git -C sing-box-for-android fetch -q --depth 1 https://github.com/SagerNet/sing-box-for-android.git "$PATCH_BASE"

apply() { # repo dir, patch dir
  if compgen -G "$2/*.patch" >/dev/null; then
    git -C "$1" -c user.name=bitproxy -c user.email=bitproxy@localhost am -q -3 "$2"/*.patch
  fi
}
apply sing-box "$ROOT/patches/core"
apply sing-box-for-android "$ROOT/patches/android"
"$ROOT/prepare-sing-tun.sh" sing-box "$W"

# BitProxy version code = upstream code * 1000 + build number (BITPROXY_BUILD, set by the release
# workflow; 0 for local builds). It always grows, so the in-app updater offers patch-only releases.
PROPS=sing-box-for-android/version.properties
UP_CODE=$(sed -n 's/^VERSION_CODE=//p' $PROPS)
VERSION_NAME=$(sed -n 's/^VERSION_NAME=//p' $PROPS)
VERSION_CODE=$((UP_CODE * 1000 + ${BITPROXY_BUILD:-0} % 1000))
sed -i "s/^VERSION_CODE=.*/VERSION_CODE=$VERSION_CODE/" $PROPS
echo "BitProxy $VERSION_NAME, version code $VERSION_CODE"

# build_libbox copies the .aar files into ../sing-box-for-android/app/libs when it exists
mkdir -p sing-box-for-android/app/libs
# libbox is built with a Go whose timers count deep sleep (see go-boottime.sh).
GO_BT=$("$ROOT/go-boottime.sh" "$W/go")
(export GOROOT=$GO_BT PATH="$GO_BT/bin:$PATH" && cd sing-box && go run ./cmd/internal/build_libbox -target android)

cd sing-box-for-android
if [ -n "${RELEASE_KEYSTORE:-}" ]; then
  cp "$RELEASE_KEYSTORE" app/release.keystore
  # Upstream's build.gradle.kts expects LOCAL_PROPERTIES base64-encoded; accept plain text here.
  export LOCAL_PROPERTIES=$(printf '%s' "${LOCAL_PROPERTIES:-}" | base64 -w0)
  trap 'rm -f app/release.keystore' EXIT # don't leave a copy of the key in the build folder
  ./gradlew --no-daemon :app:testOtherDebugUnitTest :app:assembleOtherRelease
  OUT=app/build/outputs/apk/other/release
else
  ./gradlew --no-daemon :app:testOtherDebugUnitTest :app:assembleOtherDebug
  OUT=app/build/outputs/apk/other/debug
fi
# Read by the in-app updater (GitHubUpdateChecker) from each release. Builds before the rename
# to BitProxy-* look for SFA-version-metadata.json, so publish that name too.
printf '{\n  "version_code": %s,\n  "version_name": "%s"\n}\n' "$VERSION_CODE" "$VERSION_NAME" > "$OUT/BitProxy-version-metadata.json"
cp "$OUT/BitProxy-version-metadata.json" "$OUT/SFA-version-metadata.json"
ls -la "$OUT"/*.apk "$OUT"/*-version-metadata.json
echo "APK_DIR=$W/sing-box-for-android/$OUT" >> "${GITHUB_ENV:-/dev/null}"
