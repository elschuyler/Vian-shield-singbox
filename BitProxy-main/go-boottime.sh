#!/usr/bin/env bash
# Copy the current Go toolchain to $1 and make its clock count deep sleep (CLOCK_BOOTTIME instead
# of CLOCK_MONOTONIC in runtime.nanotime). Without it Go timers stop while the phone sleeps, so
# after a long sleep WireGuard keeps using keys the server already dropped: "connected" but dead.
# Same patch as wireguard-android and Proton VPN (golang/go#24595). Prints the new GOROOT.
set -euo pipefail
DEST=$1
SRC=$(go env GOROOT)
rm -rf "$DEST" && cp -a "$SRC" "$DEST"
R=$DEST/src/runtime
sed -i 's/^#define CLOCK_MONOTONIC\(\s*\)1$/#define CLOCK_MONOTONIC\17 \/\/ BitProxy: CLOCK_BOOTTIME/' "$R/sys_linux_arm64.s" "$R/sys_linux_arm.s"
sed -i 's/MOVL\t\$1, DI \/\/ CLOCK_MONOTONIC/MOVL\t$7, DI \/\/ CLOCK_BOOTTIME/' "$R/sys_linux_amd64.s" "$R/time_linux_amd64.s"
sed -i 's/MOVL\t\$1, 0(SP)\t\/\/ CLOCK_MONOTONIC/MOVL\t$7, 0(SP)\t\/\/ CLOCK_BOOTTIME/; s/MOVL\t\$1, BX\t\t\/\/ CLOCK_MONOTONIC/MOVL\t$7, BX\t\t\/\/ CLOCK_BOOTTIME/' "$R/sys_linux_386.s"
# Fail loudly if a Go update moved the code: every Android arch must be patched.
n=$(grep -c 'CLOCK_BOOTTIME\|BitProxy: CLOCK_BOOTTIME' "$R/sys_linux_arm64.s" "$R/sys_linux_arm.s" "$R/sys_linux_amd64.s" "$R/time_linux_amd64.s" "$R/sys_linux_386.s" | awk -F: '{s+=$2} END {print s}')
if [ "$n" -lt 7 ]; then echo "go-boottime.sh: patched $n of 7 places; the Go runtime changed, update this script" >&2; exit 1; fi
echo "$DEST"
