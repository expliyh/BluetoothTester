#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-13114758}"

HOST_OS="$(uname -s)"
case "$HOST_OS" in
  Linux)
    CMDLINE_TOOLS_OS="linux"
    ;;
  Darwin)
    CMDLINE_TOOLS_OS="mac"
    ;;
  *)
    echo "[ERROR] Unsupported host OS: $HOST_OS"
    echo "[HINT] This script currently supports Linux and macOS (Darwin)."
    exit 1
    ;;
esac

if ! command -v java >/dev/null 2>&1; then
  echo "[ERROR] Java is required but not found in PATH."
  exit 1
fi

echo "[INFO] Java runtime"
java -version

echo "[INFO] Ensuring Android SDK root: $ANDROID_SDK_ROOT"
mkdir -p "$ANDROID_SDK_ROOT"

TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools"
LATEST_DIR="$TOOLS_DIR/latest"
mkdir -p "$TOOLS_DIR"

if [[ ! -x "$LATEST_DIR/bin/sdkmanager" ]]; then
  echo "[INFO] Android command line tools not found, downloading for $HOST_OS..."
  TMP_DIR="$(mktemp -d)"
  ARCHIVE="$TMP_DIR/cmdline-tools.zip"
  URL="https://dl.google.com/android/repository/commandlinetools-${CMDLINE_TOOLS_OS}-${CMDLINE_TOOLS_VERSION}_latest.zip"

  curl -fL "$URL" -o "$ARCHIVE"
  unzip -q "$ARCHIVE" -d "$TMP_DIR/unpacked"

  rm -rf "$LATEST_DIR"
  mv "$TMP_DIR/unpacked/cmdline-tools" "$LATEST_DIR"
  rm -rf "$TMP_DIR"
else
  echo "[INFO] Android command line tools already installed."
fi

SDKMANAGER="$LATEST_DIR/bin/sdkmanager"

echo "[INFO] Accepting Android licenses"
set +e
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null
license_status=$?
set -o pipefail
set -e
if [[ "$license_status" -ne 0 && "$license_status" -ne 141 ]]; then
  echo "[ERROR] Failed to accept Android licenses, exit code: $license_status"
  exit "$license_status"
fi

echo "[INFO] Installing SDK packages"
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0"

LOCAL_PROPERTIES="$ROOT_DIR/local.properties"
{
  echo "sdk.dir=$ANDROID_SDK_ROOT"
} > "$LOCAL_PROPERTIES"

echo "[INFO] Wrote $LOCAL_PROPERTIES"
echo "[INFO] Environment init complete."
