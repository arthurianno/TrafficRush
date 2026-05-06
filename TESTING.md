# Traffic Rush Testing

## A. Build Debug APK

```bash
./gradlew :app:assembleDebug
```

Run JVM unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run instrumented Compose/UI tests on a connected emulator/device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## B. Debug APK Path

```text
app/build/outputs/apk/debug/app-debug.apk
```

## C. Install With adb

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or use the helper script:

```bash
./scripts/install_debug.sh
```

Clear local app state:

```bash
./scripts/clear_app.sh
```

## D. Open Debug Panel

Run the debug build and tap the `Debug` button in the top-right corner.

The panel keeps the main controls at the top: `Reset state`, `Restart flow`, `Config source` (MOCK/REAL), and `Mock scenario`.

Long `Last URL` and `Current URL` values are truncated by default. Use `Copy` to copy a URL and `Expand` to reveal the full value.

Debug builds also accept adb intent extras for smoke testing:

```bash
adb shell am start -n com.games.playNewAdventure/.MainActivity \
  --ez debug_reset true \
  --es debug_config_source MOCK \
  --es debug_mock_scenario SUCCESS_WEBVIEW
```

## E. Mock WebView

1. Open `Debug`.
2. Tap `Reset state`.
3. Select `Config MOCK`.
4. Select `SUCCESS_WEBVIEW`.
5. Tap `Restart flow`.

Expected: Splash -> Push screen -> Accept/Skip -> WebView.

Expected mock WebView URL: `https://web.team-s.club/`.


## F. Fantic

1. Open `Debug`.
2. Tap `Reset state`.
3. Select `Config MOCK`.
4. Select `NEGATIVE_RESPONSE`.
5. Tap `Restart flow`.

Expected: Fantic screen.

The `START`, `LEVELS`, and `SCORES` buttons are separate `Image` components and are independently clickable.

## G. No Internet

1. Disable internet on the device.
2. Open `Debug`.
3. Tap `Reset state`.
4. Tap `Restart flow`.

Expected: No Internet screen. Tap `Retry` after restoring network.

## H. Last WebView URL Fallback

1. Start with `Config MOCK` -> `SUCCESS_WEBVIEW`.
2. Complete push screen with Accept or Skip.
3. Open `Debug`.
4. Select `SERVER_ERROR` or `TIMEOUT`.
5. Tap `Restart flow`.

Expected: WebView opens the stored `last_webview_url`.

For the mock success flow, the stored fallback URL should be `https://web.team-s.club/`.

## I. Real Config

1. Open `Debug`.
2. Tap `Reset state`.
3. Select `Config REAL`.
4. Tap `Restart flow`.

Expected: Result depends on `https://traficruush.com/config.php`.

## J. Push Permission

1. Select `Config MOCK` -> `SUCCESS_WEBVIEW`.
2. Tap `Restart flow`.
3. On the push screen, tap Accept or Skip.

Debug builds use `MockPushService`, so the Android system permission dialog may not appear. Release builds use `FirebasePushService` and request `POST_NOTIFICATIONS` on Android 13+ after Accept.

## K. AppsFlyer

1. Add the device GAID to AppsFlyer Test Devices.
2. Open the AppsFlyer test URL before installing or launching the app:

```text
https://app.appsflyer.com/com.games.playNewAdventure?pid=Test%20Source&c=testsub_testsub2_testsub_testsub_testsub_testsub_testsub_testsub1%20%23extra&siteid=syndicate_g&adset=testsub&af_adset=testsub3&af_c_id=testsub4&agency=Test%20Agency&af_sub1=testextra2&af_sub2=testextra3&af_sub3=testextra4&af_sub4=testextra5&af_sub5=testextra6&is_retargeting=true&deep_link_value=deep_link_test&deep_link_sub1=deep_test_sub1&advertising_id=GAID
```

3. Install and launch the app.

## Real Traffic Rush Values

- Site URL: `https://traficruush.com`
- Privacy Policy: `https://traficruush.com/privacy-policy.html`
- Support: `https://traficruush.com/support.html`
- Config URL: `https://traficruush.com/config.php`
- Mock WebView URL: `https://web.team-s.club/`

## Asset Notes

All game screens are assembled natively from individual PNG assets.

| Screen | Background | Key elements |
|---|---|---|
| `LoadingScreen` | `farm_background.png` (Crop) + 0.25 overlay | `logo_egg_flip.png`, `CircularProgressIndicator` |
| `FanticScreen` | `farm_background.png` (Crop) + 0.20 overlay | `logo_egg_flip.png`, `btn_start/levels/scores.png`, `btn_settings/home.png` |
| `PushPermissionScreen` | `farm_background.png` (Crop) + bottom gradient | `chicken_character.png`, `push_egg_top_right.png`, `push_egg_left.png`, `push_nest_eggs.png`, `btn_accept.png`, native `Text` |

Reference-only PNGs in `drawable-nodpi` (not rendered in any screen): `push_permission_screen.png`, `fantic_main.png`, `splash_screen.png`, `background.png`.

All buttons are real clickable Compose `Image` or `Text` elements — no transparent overlay click zones.

## Real Config Opt-In Test

The real config smoke test is skipped by default, so CI is not blocked when the server returns `ok=false`.

Run it explicitly with either:

```bash
RUN_REAL_CONFIG_TEST=true ./gradlew :app:testDebugUnitTest
```

or:

```bash
./gradlew :app:testDebugUnitTest -PrunRealConfigTest=true
```

The test accepts `ok=true` with a non-empty URL or `ok=false`/HTTP failure as a handled negative path.

## adb Smoke Scripts

Run a mock success smoke launch:

```bash
./scripts/run_mock_smoke.sh SUCCESS_WEBVIEW
```

Run other mock scenarios:

```bash
./scripts/run_mock_smoke.sh NEGATIVE_RESPONSE
./scripts/run_mock_smoke.sh SERVER_ERROR
./scripts/run_mock_smoke.sh TIMEOUT
./scripts/run_mock_smoke.sh EMPTY_URL
```

For No Internet, disable networking on the device first:

```bash
./scripts/run_mock_smoke.sh mock_no_internet
```
