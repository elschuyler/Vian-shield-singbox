#!/usr/bin/env bash
# Edit, run this, see the change on the emulator.
#   ./run.sh        rebuild the app only (Kotlin/UI changes)
#   ./run.sh core   also rebuild libbox (Go changes in sing-box/)
set -euo pipefail
source "${DEV:-/media/Absolute/dev}/env.sh"
# Only ever talk to the emulator, never to a phone that happens to be plugged in.
export ANDROID_SERIAL=emulator-5554
cd "$(dirname "$(readlink -f "$0")")/sing-box"

if [ "${1:-}" = core ]; then
  # Same Go clock patch as release builds (go-boottime.sh).
  GO_BT=$(../go-boottime.sh "$DEV/go-boottime")
  # Patched sing-tun from the local ../sing-tun checkout (branch bitproxy); go.mod is restored after.
  [ -d ../sing-tun ] && go mod edit -replace "github.com/sagernet/sing-tun=$(cd ../sing-tun && pwd)"
  (export GOROOT=$GO_BT PATH="$GO_BT/bin:$PATH" && go run ./cmd/internal/build_libbox -target android -platform android/amd64,android/arm64) || { git checkout -q go.mod go.sum; exit 1; }
  git checkout -q go.mod go.sum
  mkdir -p clients/android/app/libs && cp libbox*.aar clients/android/app/libs/
fi

if ! adb get-state >/dev/null 2>&1; then  # emulator not running
  QT_QPA_PLATFORM=xcb setsid nohup emulator -avd sfa -no-snapshot-save >$DEV/emulator.log 2>&1 </dev/null &
  adb wait-for-device
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
fi

cd clients/android && ./gradlew :app:installOtherDebug --console=plain -q
adb shell am force-stop com.zonicisalive.bitproxy
adb shell monkey -p com.zonicisalive.bitproxy -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo "launched"
