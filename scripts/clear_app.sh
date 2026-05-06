#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/adb_common.sh"
ADB="$(resolve_adb)"

"$ADB" shell pm clear com.games.playNewAdventure
