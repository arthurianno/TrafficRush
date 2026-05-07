# Implementation Status

## What Is Implemented

| Feature | Status | Notes |
|---|---|---|
| Startup flow (UNKNOWN → WEBVIEW / FANTIC / NoInternet) | ✅ | AppStartupController |
| Remote config fetch | ✅ | RemoteConfigService + MockWebServer test |
| Mock config switch (debug) | ✅ | SwitchingConfigService, DebugPanel |
| AppsFlyer attribution | ✅ | Release only; MockAttributionService in debug |
| Firebase push token | ✅ | Release only; MockPushService in debug |
| Push permission prompt (3-day repeat) | ✅ | shouldAskPushPermission() |
| WebView screen | ✅ | Back-press handled, URL tracking |
| No Internet screen + Retry | ✅ | |
| Loading screen | ✅ | farm_background + logo + spinner |
| FanticScreen (game menu) | ✅ | farm_background + logo + START/LEVELS/SCORES buttons |
| PushPermissionScreen | ✅ | farm_background, decorative eggs, native text, ACCEPT/Skip |
| Debug panel | ✅ | Reset, Restart, Config source, Mock scenario, URL copy |
| Edge-to-edge / safe area | ✅ | systemBarsPadding / navigationBarsPadding |

## Build

```bash
# Debug APK
./gradlew :app:assembleDebug
# APK path: app/build/outputs/apk/debug/app-debug.apk

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
# Unit tests (no device needed)
./gradlew :app:testDebugUnitTest

# Real config smoke test (requires network)
RUN_REAL_CONFIG_TEST=true ./gradlew :app:testDebugUnitTest

# Instrumented / Compose UI tests (requires device or emulator)
./gradlew :app:connectedDebugAndroidTest
```

## Smoke Scripts

```bash
# Startup → WebView success
./scripts/run_mock_smoke.sh SUCCESS_WEBVIEW

# Startup → Fantic (negative response)
./scripts/run_mock_smoke.sh NEGATIVE_RESPONSE
```

## Manual Verification Checklist

- [ ] First launch → Loading → WebView (real config)
- [ ] First launch → Loading → Fantic (negative/no URL in config)
- [ ] First launch offline → No Internet screen → Retry works
- [ ] Push prompt appears on first WebView launch
- [ ] Push prompt does NOT appear within 3 days of decline
- [ ] Push prompt reappears after 3 days
- [ ] ACCEPT triggers system permission dialog
- [ ] Skip stores decline timestamp and goes to WebView
- [ ] Debug panel: Reset state → re-runs startup flow
- [ ] Debug panel: Config source switch (MOCK ↔ REAL)
- [ ] Debug panel: Mock scenario switch
- [ ] Debug panel: URL copy works
- [ ] Debug panel: URL expand/collapse works
- [ ] Debug panel: Clear last URL
- [ ] Debug button does not overlap status bar
- [ ] FanticScreen: START / LEVELS / SCORES → "Coming soon" toast
- [ ] WebView: back-press finishes activity

## AppsFlyer Test Install

1. Open AppConstants.APPSFLYER_TEST_URL in a browser on a test device.
2. Install the app from the Play Store (or sideload debug APK).
3. Launch — AppsFlyer should attribute the install.
4. Verify in AppsFlyer dashboard or logcat (`AppsFlyer` tag).

## Known TODOs Before Release

- [ ] Replace MockAttributionService / MockPushService with real services in release (already done via BuildConfig.DEBUG check).
- [ ] P3 architecture hardening: move mock providers, debug intents, and DebugPanel wiring from `main` to `src/debug` to keep release source surface smaller.
- [ ] P3 release hardening: enable release shrink/obfuscation after validating AppsFlyer/Firebase/config behavior with keep rules.
- [ ] P3 WebView hardening: `WEBVIEW_HOST_ALLOWLIST` is documented in `WebViewScreen`; keep `ENFORCE_WEBVIEW_HOST_ALLOWLIST=false` until remote config confirms whether campaign/partner domains may be returned.
- [ ] Push rich image payloads are detected and logged, but BigPictureStyle/image download rendering is not implemented yet.
- [ ] Verify AppsFlyer dev key in AppConstants.APPSFLYER_DEV_KEY.
- [ ] Verify Firebase project config in google-services.json matches AppConstants.FIREBASE_PROJECT_ID.
- [ ] Review CONFIG_URL endpoint response format before going live.
- [ ] Consider WebP conversion for large PNGs in drawable-nodpi to reduce APK size (~4 MB of PNGs currently).
- [ ] START / LEVELS / SCORES buttons on FanticScreen show "coming soon" — wire up real game logic when ready.

## UI Architecture (post-refactor)

```
ui/
  GameUiTokens.kt     — all dp/sp/alpha layout constants (single source of truth)
  GameComponents.kt   — reusable Compose components (FarmBackground, DecoImage, etc.)
  AppScreens.kt       — screen-level composables (LoadingScreen, FanticScreen, PushPermissionScreen, NoInternetScreen)
  StartupHost.kt      — navigation state machine + DebugPanel wiring
  DebugPanel.kt       — debug-only overlay (excluded from release via BuildConfig.DEBUG)
  UiTestTags.kt       — test tag constants
  WebViewScreen.kt    — WebView wrapper
  theme/              — MaterialTheme config
```

## Reference Assets (not used in UI)

The following files in `drawable-nodpi` exist for design reference only and are not rendered in any screen:

- `push_permission_screen.png`
- `fantic_main.png`
- `splash_screen.png`
- `background.png`
