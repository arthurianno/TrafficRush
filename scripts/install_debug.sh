#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/adb_common.sh"
ADB="$(resolve_adb)"

./gradlew :app:assembleDebug
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
