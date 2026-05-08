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

Use `Close` inside the panel, or tap `Debug` again, to collapse the overlay.

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

Expected mock WebView URL base: `https://web.team-s.club/`. In mock attribution mode the app may append `deep_link_value=deep_link_test` and `deep_link_sub1=deep_test_sub1` so the client-side deeplink chain can be tested without injecting fake values in REAL mode.

Safe area expectations:

- WebView content must start below the status bar/system icons.
- WebView content must not extend under the navigation bar.
- Debug button must stay in the safe area and must not cover the top of the web page.
- Test-site buttons such as `Go to Example.com` are external WebView content and should remain visible only as part of the page.
- Back behavior must stay unchanged: WebView history uses `goBack()`, and the first page sends the task to background with `moveTaskToBack(true)`.

WebView rendering diagnostics:

- Open Chrome on the desktop and navigate to `chrome://inspect/#devices`.
- Select the `Traffic Rush` WebView for `com.games.playNewAdventure`.
- Compare `https://web.team-s.club/` in desktop/mobile Chrome and in the app WebView.
- Check console output for critical CSS/JS/resource errors.
- Debug builds log WebView lifecycle, HTTP/resource errors, console messages, and User-Agent under the `TrafficRushWebView` logcat tag.


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

For the mock success flow, the stored fallback URL should use `https://web.team-s.club/` as the base URL. If mock attribution is active, `deep_link_value=deep_link_test` and `deep_link_sub1=deep_test_sub1` may be present in the URL.

## I. Real Config

1. Open `Debug`.
2. Tap `Reset state`.
3. Select `Config REAL`.
4. Tap `Restart flow`.

Expected: Result depends on `https://traficruush.com/config.php`.

Use the `Last Config Response` section in the Debug panel to inspect:

- `HTTP status`
- `Result`: `Success`, `Negative`, or `TransientError`
- `ok`
- `url`
- `error`
- whether the request contained `af_id`, `push_token`, and `firebase_project_id`
- `af_status` and `deep_link_value`
- the startup decision and reason

If the server returns `Negative`, Fantic is expected. If the request has a transient network/server/parsing error on first launch, NoInternet is expected and Fantic must not be saved. If the server returns `ok=true` with a non-empty `url`, WebView should open and the URL should be saved.

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

## K1. Почему WebView пишет Нет deep_link_value/deep_link_sub

Эти параметры не появляются сами. В REAL mode клиент не подставляет fake `deep_link_value` или `deep_link_sub`: они должны прийти из рабочей AppsFlyer/OneLink ссылки, настроенной на package `com.games.playNewAdventure`.

При запуске приложения с иконки `deep_link_value` и `deep_link_sub1` ожидаемо будут `MISSING`. Если OneLink открывает страницу `The app you are looking for is unavailable`, приложение технически не сможет получить эти параметры, потому что AppsFlyer не отдаёт валидный click/deeplink event для этого приложения.

Для реальной проверки заказчик должен дать рабочий AppsFlyer/OneLink URL, настроенный на `com.games.playNewAdventure`, содержащий минимум:

```text
deep_link_value=deep_link_test
deep_link_sub1=deep_test_sub1
```

Проверка цепочки в DebugPanel:

- `AppsFlyer deep_link_value` и `AppsFlyer deep_link_sub1` показывают, получил ли SDK параметры.
- `last deeplink source` должен быть `UDL`, `conversion`, `appOpenAttribution` или `none`.
- `last config contained deep_link_value` и `last config contained any deep_link_sub` показывают, ушли ли параметры в `config.php` без переименования.
- `last config returned URL contains ...` показывает, вернул ли сервер WebView URL с этими параметрами.
- `current WebView URL contains ...` показывает, какой URL реально открыт в WebView.

Диагностика:

- AppsFlyer fields = `MISSING`: проблема в OneLink/AppsFlyer input или приложение запущено не из рабочей ссылки.
- AppsFlyer fields есть, но `last config contained ... = false`: проблема клиентского request.
- `last config contained ... = true`, но `last config returned URL contains ... = false`: `config.php` получил параметры, но вернул WebView URL без них. WebView-тест требует эти параметры именно в финальном URL.
- `last config returned URL contains ... = true`, но `current WebView URL contains ... = false`: WebView открыл другой URL, нужно проверять launch source/navigation.

Mock-only проверка клиентской логики:

1. В DebugPanel выбрать `Config: MOCK`, `Attribution: MOCK`, `Scenario: SUCCESS_WEBVIEW`.
2. Нажать `Reset state`, затем `Restart flow`.
3. Ожидаемо:
   - `AppsFlyer deep_link_value: deep_link_test`;
   - `AppsFlyer deep_link_sub1: deep_test_sub1`;
   - `has required deeplink params: true`;
   - `last config contained deep_link_value: true`;
   - `last config contained any deep_link_sub: true`;
   - `last config returned URL contains deep_link_value: true`;
   - `last config returned URL contains any deep_link_sub: true`;
   - `current WebView URL contains deep_link_value: true`;
   - `current WebView URL contains any deep_link_sub: true`.

Mock mode нужен только для проверки клиентской цепочки. В REAL mode fake deeplink параметры не добавляются.

## L. AppsFlyer Dev Key vs AppsFlyer UID

- AppsFlyer Dev Key: `TJqxhS6yxVaJvsjJgQ78hZ`. This value is only for SDK initialization.
- AppsFlyer UID / `af_id`: runtime device/install ID from `AppsFlyerLib.getInstance().getAppsFlyerUID(context)`.
- Real push test URL must use the runtime UID, not the dev key and not mock values:

```text
https://web.team-s.club/check_push?af_id=<AppsFlyer UID>
```

For real push testing in a debug build:

1. Open Debug panel.
2. Set `Config: REAL`.
3. Set `Attribution: REAL`.
4. Set `Push token: REAL`.
5. Tap `Reset state`.
6. Tap `Restart flow`.
7. Accept notification permission.
8. Wait until `AppsFlyer UID / af_id`, FCM token, notification permission, `af_id sent to config`, and `Push token sent to config` are all ready.
9. Tap `Copy push URL` and open the copied `https://web.team-s.club/check_push?af_id=<AppsFlyer UID>` link in a browser.

Do not use `mock_af_id_123` for real push testing, and do not leave `Config: MOCK`: mock config does not send the real `af_id` + `push_token` to the server. `Copy push URL` stays disabled for mock IDs, blank UID, `Config: MOCK`, missing FCM token, missing notification permission, or a config request that has not sent the current `af_id` + `push_token`.

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
