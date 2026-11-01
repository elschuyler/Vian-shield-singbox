#!/usr/bin/env bash
# Try the BitProxy patches on a newer upstream before switching versions.env to it:
# fresh clone, apply all patches, build libbox + a debug APK, run the unit tests.
#   ./check-upstream.sh                       newest sing-box release tag + SFA main
#   ./check-upstream.sh v1.15.0 main          a specific sing-box tag/branch + SFA branch/commit
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
source "${DEV:-/media/Absolute/dev}/env.sh"
CORE=${1:-$(git ls-remote --tags --refs https://github.com/SagerNet/sing-box.git 'v*' | sed 's|.*/||' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -1)}
SFA=${2:-main}
LOG=$DEV/check-upstream.log
echo "Trying sing-box $CORE + SFA $SFA (log: $LOG)"
if TRY_SING_BOX_REF=$CORE TRY_SFA_REF=$SFA WORK_DIR=$DEV/upstream-check ./build-release.sh > "$LOG" 2>&1; then
  echo "OK: patches apply, libbox and the app build, unit tests pass."
  echo "Next: set SING_BOX_TAG=$CORE and SFA_COMMIT to that commit in versions.env, rebase the local branches, export-patches.sh."
else
  echo "FAILED. Where:"
  grep -E 'Patch failed|error: patch|CONFLICT|e: |FAILED|What went wrong|FATAL' -A2 "$LOG" | head -20
  exit 1
fi
