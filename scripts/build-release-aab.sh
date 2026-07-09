#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#
# Usage:
#   ./scripts/build-release-aab.sh [--prompt] [--release-version MAJOR.MINOR[.PATCH]]
#
# Bumps versionCode by 1 and, by default, sets the versionName patch (.x) to match the new
# versionCode — e.g. versionCode 6 -> versionName 1.0.6 (major.minor are preserved). Pass
# --release-version to pin a significant release name (e.g. --release-version 1.1.0); that exact
# name is used for this build, and later default builds resume tracking the patch to versionCode
# on top of the new major.minor.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROPS="keystore.properties"
VERSION_FILE="version.properties"
AAB="app/build/outputs/bundle/release/app-release.aab"
APK="app/build/outputs/apk/release/app-release.apk"
RELEASES_DIR="releases"
PLACEHOLDER_STORE_PASSWORD="your-store-password"
PLACEHOLDER_KEY_PASSWORD="your-key-password"

read_prop() {
  local key="$1"
  grep "^${key}=" "$PROPS" | cut -d= -f2- | tr -d '\r'
}

read_version_prop() {
  local key="$1"
  grep "^${key}=" "$VERSION_FILE" | cut -d= -f2- | tr -d '\r'
}

write_version_prop() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "$VERSION_FILE"; then
    if [[ "$(uname)" == "Darwin" ]]; then
      sed -i '' "s/^${key}=.*/${key}=${value}/" "$VERSION_FILE"
    else
      sed -i "s/^${key}=.*/${key}=${value}/" "$VERSION_FILE"
    fi
  else
    echo "${key}=${value}" >> "$VERSION_FILE"
  fi
}

bump_version_code() {
  if [[ ! -f "$VERSION_FILE" ]]; then
    cat > "$VERSION_FILE" <<'EOF'
versionCode=1
versionName=1.0.0
EOF
  fi

  local current next
  current="$(read_version_prop versionCode)"
  next=$((current + 1))
  write_version_prop versionCode "$next"
  echo "Bumped versionCode: ${current} -> ${next}"
  apply_version_name "$next"
}

# Sets versionName. Default: patch (.x) tracks the versionCode, preserving major.minor
# (1.0.0 + versionCode 6 -> 1.0.6). A pinned significant release (--release-version) is used as-is.
apply_version_name() {
  local version_code="$1"

  if [[ -n "$RELEASE_VERSION" ]]; then
    write_version_prop versionName "$RELEASE_VERSION"
    echo "versionName pinned to significant release: $RELEASE_VERSION"
    return
  fi

  local current_name major minor new_name
  current_name="$(read_version_prop versionName)"
  major="$(echo "$current_name" | cut -d. -f1)"
  minor="$(echo "$current_name" | cut -d. -f2)"
  [[ -z "$major" ]] && major=1
  [[ -z "$minor" ]] && minor=0
  new_name="${major}.${minor}.${version_code}"
  write_version_prop versionName "$new_name"
  echo "versionName: ${current_name} -> ${new_name} (patch tracks versionCode)"
}

verify_keystore() {
  local store_file="$1"
  local store_password="$2"
  local key_alias="$3"
  local key_password="$4"

  if keytool -list \
    -keystore "$store_file" \
    -alias "$key_alias" \
    -storepass "$store_password" \
    -keypass "$key_password" >/dev/null 2>&1; then
    return 0
  fi

  if keytool -list \
    -keystore "$store_file" \
    -alias "$key_alias" \
    -storepass "$store_password" >/dev/null 2>&1; then
    return 0
  fi

  return 1
}

prompt_passwords() {
  echo "Enter the passwords you chose when running keytool -genkey."
  read -rsp "Keystore password: " KEYSTORE_PASSWORD
  echo
  read -rsp "Key password [same as keystore if you pressed Enter at keytool]: " KEY_PASSWORD
  echo
  if [[ -z "$KEY_PASSWORD" ]]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
  fi
  export KEYSTORE_PASSWORD KEY_PASSWORD
}

archive_release_artifacts() {
  local version_name version_code build_stamp artifact_base archived_aab archived_apk

  version_name="$(read_version_prop versionName)"
  version_code="$(read_version_prop versionCode)"
  build_stamp="$(date +%Y%m%d-%H%M%S)"

  if [[ -z "$version_name" || -z "$version_code" ]]; then
    echo "Could not read versionName/versionCode from $VERSION_FILE"
    exit 1
  fi

  mkdir -p "$RELEASES_DIR"
  artifact_base="garagedoor-v${version_name}-${version_code}-${build_stamp}"
  archived_aab="${RELEASES_DIR}/${artifact_base}.aab"
  archived_apk="${RELEASES_DIR}/${artifact_base}.apk"

  cp "$AAB" "$archived_aab"
  cp "$APK" "$archived_apk"

  echo
  echo "Archived release artifacts:"
  ls -lh "$archived_aab" "$archived_apk"
  echo
  echo "All builds in ${RELEASES_DIR}/:"
  ls -lh "$RELEASES_DIR"
  echo
  echo "Upload to Play Console -> Internal testing:"
  echo "  $ROOT/$archived_aab"
}

if [[ ! -f "$PROPS" ]]; then
  echo "Missing $PROPS"
  echo
  echo "One-time setup:"
  echo "  1. cp keystore.properties.example keystore.properties"
  echo "  2. Create a keystore (if you don't have one yet):"
  echo "       keytool -genkey -v \\"
  echo "         -keystore garage-upload.jks \\"
  echo "         -keyalg RSA -keysize 2048 -validity 10000 \\"
  echo "         -alias upload"
  echo "  3. Either edit keystore.properties with your passwords, or run:"
  echo "       ./scripts/build-release-aab.sh --prompt"
  exit 1
fi

STORE_FILE="$(read_prop storeFile)"
KEY_ALIAS="$(read_prop keyAlias)"

if [[ -z "$STORE_FILE" || -z "$KEY_ALIAS" ]]; then
  echo "keystore.properties must set storeFile= and keyAlias="
  exit 1
fi

if [[ ! -f "$STORE_FILE" ]]; then
  echo "Keystore not found: $STORE_FILE"
  echo "Create it with keytool (see keystore.properties.example) or fix storeFile in $PROPS"
  exit 1
fi

STORE_PASSWORD="$(read_prop storePassword)"
KEY_PASSWORD="$(read_prop keyPassword)"

USE_PROMPT=false
RELEASE_VERSION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --prompt) USE_PROMPT=true; shift ;;
    --release-version) RELEASE_VERSION="${2:-}"; shift 2 ;;
    --release-version=*) RELEASE_VERSION="${1#*=}"; shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [[ -n "$RELEASE_VERSION" && ! "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
  echo "Invalid --release-version '$RELEASE_VERSION' (expected MAJOR.MINOR or MAJOR.MINOR.PATCH)"
  exit 1
fi

if [[ "$USE_PROMPT" == true ]] ||
  [[ -z "${KEYSTORE_PASSWORD:-}" && -z "${KEY_PASSWORD:-}" && (
    "$STORE_PASSWORD" == "$PLACEHOLDER_STORE_PASSWORD" ||
    "$KEY_PASSWORD" == "$PLACEHOLDER_KEY_PASSWORD" ||
    -z "$STORE_PASSWORD" ||
    -z "$KEY_PASSWORD"
  ) ]]; then
  echo "Signing passwords are read from keystore.properties or env vars, not keytool."
  echo "Your keystore.properties still has placeholder values — prompting instead."
  echo
  prompt_passwords
else
  export KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-$STORE_PASSWORD}"
  export KEY_PASSWORD="${KEY_PASSWORD:-$KEY_PASSWORD}"
fi

if ! verify_keystore "$STORE_FILE" "$KEYSTORE_PASSWORD" "$KEY_ALIAS" "$KEY_PASSWORD"; then
  echo
  echo "Keystore password check failed for: $STORE_FILE (alias: $KEY_ALIAS)"
  echo
  echo "Common fixes:"
  echo "  - Use the passwords from keytool -genkey, not the placeholders in keystore.properties"
  echo "  - If you pressed Enter for 'key password', store and key passwords are the same"
  echo "  - Re-run with prompts: ./scripts/build-release-aab.sh --prompt"
  echo "  - Or export env vars: KEYSTORE_PASSWORD=... KEY_PASSWORD=... ./scripts/build-release-aab.sh"
  exit 1
fi

echo "Keystore verified."
bump_version_code
echo "Building signed release AAB and APK..."
./gradlew bundleRelease assembleRelease

if [[ ! -f "$AAB" ]]; then
  echo "Build finished but AAB not found at: $AAB"
  exit 1
fi

if [[ ! -f "$APK" ]]; then
  echo "Build finished but APK not found at: $APK"
  exit 1
fi

archive_release_artifacts
