#!/usr/bin/env bash
# Creates the BitProxy release signing key with a random password, stored next to it.
# BACK UP the whole keys folder: if it is lost, updates can't be installed over the app.
set -euo pipefail
source "${DEV:-/media/Absolute/dev}/env.sh"
DIR=$DEV/keys
KS=$DIR/bitproxy-release.keystore
PROPS=$DIR/signing.properties
mkdir -p "$DIR" && chmod 700 "$DIR"
[ -e "$KS" ] && { echo "$KS already exists, not overwriting"; exit 1; }
PASS=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)
keytool -genkeypair -keystore "$KS" -alias bitproxy -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=BitProxy" -storepass "$PASS" -keypass "$PASS" >/dev/null 2>&1
printf 'KEYSTORE_PASS=%s\nALIAS_NAME=bitproxy\nALIAS_PASS=%s\n' "$PASS" "$PASS" > "$PROPS"
chmod 600 "$KS" "$PROPS"
echo "Created $KS and $PROPS (password inside). Back up $DIR."
echo "GitHub secrets: KEYSTORE_BASE64 = base64 -w0 $KS ; KEYSTORE_PASS / ALIAS_PASS from $PROPS ; ALIAS_NAME = bitproxy"
