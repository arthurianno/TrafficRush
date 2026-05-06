#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/adb_common.sh"
ADB="$(resolve_adb)"

RAW_SCENARIO="${1:-SUCCESS_WEBVIEW}"
case "$RAW_SCENARIO" in
  mock_success_webview|SUCCESS_WEBVIEW)
    SCENARIO="SUCCESS_WEBVIEW"
    ;;
  mock_negative_response|NEGATIVE_RESPONSE)
    SCENARIO="NEGATIVE_RESPONSE"
    ;;
  mock_server_error|SERVER_ERROR)
    SCENARIO="SERVER_ERROR"
    ;;
  mock_timeout|TIMEOUT)
    SCENARIO="TIMEOUT"
    ;;
  mock_empty_url|EMPTY_URL)
    SCENARIO="EMPTY_URL"
    ;;
  mock_no_internet)
    SCENARIO="SUCCESS_WEBVIEW"
    echo "Disable Wi-Fi/mobile data on the device before continuing to validate No Internet."
    ;;
  *)
    echo "Unknown scenario: $RAW_SCENARIO"
    echo "Allowed: SUCCESS_WEBVIEW, NEGATIVE_RESPONSE, SERVER_ERROR, TIMEOUT, EMPTY_URL, mock_no_internet"
    exit 2
    ;;
esac

./scripts/install_debug.sh
./scripts/clear_app.sh

"$ADB" shell am start \
  -n com.games.playNewAdventure/.MainActivity \
  --ez debug_reset true \
  --es debug_config_source MOCK \
  --es debug_mock_scenario "$SCENARIO"

echo "Launched Traffic Rush debug with Config MOCK and scenario $SCENARIO."
echo "Open the Debug panel in the app to verify Config source, Mock scenario, Last URL, Current URL, and click-zone overlay."
