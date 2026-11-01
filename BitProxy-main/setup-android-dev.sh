#!/usr/bin/env bash
# One-time setup (no sudo) for building sing-box libbox + SFA Android app. Installs to $DEV (a folder with env.sh, see README).
set -euo pipefail
source "${DEV:-/media/Absolute/dev}/env.sh"
SDK=$ANDROID_HOME; T=$(mktemp -d -p $DEV)
mkdir -p "$SDK" "$ANDROID_AVD_HOME"

[ -x $GOROOT/bin/go ] || { curl -fsSL https://go.dev/dl/go1.26.8.linux-amd64.tar.gz | tar -xz -C $DEV; }
[ -x $JAVA_HOME/bin/java ] || { mkdir -p $JAVA_HOME; curl -fsSL 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz' | tar -xz -C $JAVA_HOME --strip-components=1; }
[ -x $DEV/android-studio/bin/studio.sh ] || { curl -fsSL https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.1.4.7/android-studio-quail4-linux.tar.gz | tar -xz -C $DEV; }
if [ ! -x $SDK/cmdline-tools/latest/bin/sdkmanager ]; then
  curl -fsSLo $T/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip
  unzip -q $T/clt.zip -d $T && mkdir -p $SDK/cmdline-tools && mv $T/cmdline-tools $SDK/cmdline-tools/latest
fi
rm -rf $T

SM=$SDK/cmdline-tools/latest/bin/sdkmanager
(yes || true) | $SM --licenses >/dev/null
$SM "cmdline-tools;latest" "platform-tools" "emulator" "build-tools;36.0.0" "platforms;android-36" "platforms;android-37.1" \
    "ndk;28.0.13004108" "system-images;android-36;google_apis;x86_64" | grep -v '^\[' || true
echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd -n sfa -k "system-images;android-36;google_apis;x86_64" -d pixel_9a --force

cd "$(dirname "$(readlink -f "$0")")/sing-box"
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.13 github.com/sagernet/gomobile/cmd/gobind@v0.1.13
go install golang.org/x/tools/gopls@latest
# build_libbox copies the .aar into ../sing-box-for-android/app/libs
ln -sfn sing-box/clients/android ../sing-box-for-android
go mod download
echo DONE
