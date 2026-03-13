#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
SDKMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

if [[ ! -x "$SDKMANAGER" ]]; then
  echo "[ERROR] sdkmanager not found at $SDKMANAGER"
  echo "[HINT] Run scripts/init-android-env.sh first."
  exit 1
fi

cd "$ROOT_DIR"

echo "[INFO] Java runtime"
java -version

echo "[INFO] Gradle runtime"
./gradlew -version

echo "[INFO] Refreshing installed SDK package metadata"
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --update

echo "[INFO] Verifying required packages"
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --install \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0"

if [[ ! -f "$ROOT_DIR/local.properties" ]]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > "$ROOT_DIR/local.properties"
  echo "[INFO] Created local.properties"
fi

echo "[INFO] Running Gradle sanity task"
./gradlew help --no-daemon --console=plain

echo "[INFO] Maintenance check completed."
