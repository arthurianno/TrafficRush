# Orientation / State preservation

Этот раздел содержит чеклист для ручной проверки сохранения состояния приложения при повороте экрана (Activity recreation).

## Проверки

### 1. WebView
- Открыть `web.team-s.club` (или любой другой стартовый URL).
- Перейти на вложенный тестовый экран/страницу (например, кликнуть по ссылке).
- Ввести любой текст в input/форму на странице (если есть).
- Проскроллить страницу вниз.
- Повернуть устройство: Portrait → Landscape → Portrait.
- Убедиться, что:
  - Текущая страница не сбросилась на стартовую.
  - Текст в input/форме сохранился (стандартное поведение `WebView.saveState`).
  - Позиция скролла сохранилась.
  - Кнопка "Назад" возвращает на предыдущую страницу внутри WebView, а не закрывает приложение.

### 2. Redirect test
- Пройти тест с многочисленными редиректами в браузере (или имитировать его).
- Дождаться окончания редиректов и успешной загрузки финальной страницы.
- Повернуть экран.
- Убедиться, что WebView остаётся на финальной странице и не пытается начать процесс редиректов заново, вызывая сброс.

### 3. File upload
- Открыть тестовую страницу загрузки файла.
- Нажать на кнопку "Upload".
- Повернуть экран ДО выбора файла (когда системный диалог Chooser/Gallery открыт).
- Повернуть экран ПОСЛЕ выбора файла (но до отправки формы).
- Убедиться, что диалог выбора файла (Camera/Gallery) не ломается и загрузка файла проходит успешно.

### 4. PushPermission
- Очистить данные приложения или запустить чистую установку, чтобы попасть на кастомный экран запроса Push-уведомлений.
- Увидеть кастомный экран Push Permission.
- Повернуть экран.
- Убедиться, что экран не пропал, а кнопки "Accept" и "Skip" всё ещё активны и корректно выполняют свои действия (открывают системный диалог или переходят дальше).

### 5. NoInternet
- Отключить интернет на устройстве (Airplane mode).
- Запустить приложение, дождаться появления экрана `NoInternet`.
- Повернуть экран.
- Убедиться, что экран `NoInternet` остаётся на месте, текст корректен, а кнопка "Retry" нажимается.

### 6. Fantic
- В DebugPanel переключить Mock Config Scenario на эмуляцию режима Fantic (или запустить приложение с реальным конфигом, возвращающим Fantic).
- Дождаться появления заглушки / Fantic экрана.
- Повернуть экран.
- Убедиться, что экран остаётся Fantic, и приложение не запускает заново процесс загрузки `Loading` или запроса конфигурации.

---

# Deep links from WebView

Этот раздел проверяет обработку ссылок `intent://`, `paytmmp://`, `phonepe://`, `bankid://` и передачу параметров AppsFlyer.

## A) Проверка external schemes (приложение установлено)
**Предусловие**: На устройстве установлено соответствующее приложение (Paytm, PhonePe, BankID).
1. Открыть WebView.
2. Открыть тестовую страницу с ссылками `paytmmp://`, `phonepe://`, `bankid://` (или временно использовать локальный HTML).
3. Нажать `paytmmp://`.
4. **Ожидаемо**: открывается Paytm или системный chooser.
5. Вернуться назад в Traffic Rush.
6. **Ожидаемо**: WebView показывает предыдущую страницу, ошибки (`ERR_UNKNOWN_URL_SCHEME`) нет.
7. Повторить для `phonepe://` и `bankid://`.

## B) Проверка без установленного приложения
1. Нажать deeplink, приложение для которого не установлено.
2. **Ожидаемо**:
   - приложение не падает (`ActivityNotFoundException` обрабатывается).
   - WebView не показывает пустую страницу ошибки (`ERR_UNKNOWN_URL_SCHEME`).
   - остаётся предыдущая валидная страница.
   - если у `intent://` ссылки был параметр `browser_fallback_url` — он открывается.

## C) Проверка AppsFlyer params
1. Удалить приложение с устройства для симуляции чистой установки.
2. Открыть тестовую ссылку AppsFlyer/OneLink, содержащую:
   - `deep_link_value=deep_link_test`
   - `deep_link_sub1=deep_test_sub1`
3. Установить APK и запустить приложение.
4. Для debug APK в DebugPanel выставить `Config: REAL`, `Attribution: REAL`, `Push token: REAL`, затем нажать `Reset state` или `Restart flow`.
5. В DebugPanel прокрутить до секции "Deep Link Params" и проверить:
   - `deep_link_value`: deep_link_test
   - `deep_link_sub1`: deep_test_sub1
   - `has required deeplink params`: true
   - `last config contained deep_link_value`: true
   - `last config contained any deep_link_sub`: true
   - `current WebView URL contains deep_link_value`: true
   - `current WebView URL contains any deep_link_sub`: true

Если `deep_link_value/deep_link_sub1` получены и `last config contained ...` = true, но `current WebView URL contains ...` = false, клиент отправил параметры в `config.php`, а сервер вернул WebView URL без этих параметров. Тест внутри WebView требует `deep_link_value` и хотя бы один `deep_link_sub` именно в финальном URL.

## D) Почему WebView пишет Нет deep_link_value/deep_link_sub
Эти параметры не появляются сами. В REAL mode приложение не подставляет fake `deep_link_value` или `deep_link_sub`: они должны прийти из рабочей AppsFlyer/OneLink ссылки, настроенной на `com.games.playNewAdventure`.

При запуске приложения с иконки `deep_link_value` и `deep_link_sub1` ожидаемо будут `MISSING`. Если OneLink показывает `The app you are looking for is unavailable`, приложение технически не может получить эти параметры, потому что AppsFlyer не отдаёт валидный deeplink/click event для этого package.

Для реальной проверки заказчик должен дать рабочий AppsFlyer/OneLink URL, содержащий минимум:
- `deep_link_value=deep_link_test`
- `deep_link_sub1=deep_test_sub1`

DebugPanel показывает всю цепочку:
- `AppsFlyer deep_link_value` и `AppsFlyer deep_link_sub1` — получил ли SDK параметры.
- `any deep_link_sub` — есть ли хотя бы один `deep_link_sub1..deep_link_sub10`.
- `has required deeplink params` — есть ли `deep_link_value` и хотя бы один `deep_link_sub`.
- `last deeplink source` — `UDL`, `conversion`, `appOpenAttribution` или `none`.
- `last config contained deep_link_value` и `last config contained any deep_link_sub` — ушли ли параметры в `config.php`.
- `last config returned URL contains ...` — вернул ли сервер URL с параметрами.
- `current WebView URL contains ...` — что реально открыто в WebView.

Интерпретация:
- AppsFlyer fields = `MISSING`: проблема в рабочей OneLink/AppsFlyer ссылке или запуск был не из deeplink.
- AppsFlyer fields есть, но `last config contained ... = false`: проблема клиентского request.
- `last config contained ... = true`, но `last config returned URL contains ... = false`: `config.php` получил параметры, но вернул WebView URL без них. WebView-тест требует параметры именно в финальном URL.
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

Mock mode проверяет только клиентскую цепочку. В REAL mode fake параметры не добавляются.

---

# ADB/Logcat Диагностика

## Logcat для WebView/deeplinks:
```bash
adb logcat | grep -i -E "WebView|deeplink|deep_link|external|intent|ERR_UNKNOWN_URL_SCHEME|ActivityNotFound"
```

## Logcat для AppsFlyer:
```bash
adb logcat | grep -i -E "AppsFlyer|DeepLink|deep_link_value|deep_link_sub|conversion"
```

---

# Push registration / UNREGISTERED

Если `check_push` возвращает:

```text
FCM errorCode: UNREGISTERED
message: Requested entity was not found
Zero successful push notifications sent
```

Firebase отверг `push_token` как невалидный. Обычно это означает, что сервер отправляет push на старый token.

Возможные причины:
- приложение было удалено или переустановлено;
- сервер хранит старый token для текущего `af_id`;
- новый FCM token не был отправлен в `config.php`;
- сервер не обновил token для `af_id`;
- Firebase project на сервере не совпадает с `app/google-services.json` (`project_id=app-01-c1c8f`, `project_number=207704284021`).

## Manual fix/check
1. Uninstall app.
2. Install fresh APK.
3. Launch app.
4. В DebugPanel выставить `Config: REAL`, `Attribution: REAL`, `Push token: REAL`.
5. Нажать `Reset state` или `Restart flow`.
6. Дождаться:
   - `AppsFlyer UID / af_id` не пустой и не `mock`;
   - `FCM token: available`;
   - `Latest FCM token sent to config: true`;
   - `firebase_project_id sent to config: true`;
   - `bundle_id sent to config: true`;
   - `Ready for push test: true`.
7. Скопировать новый URL кнопкой `Copy push URL`.
8. Открыть `https://web.team-s.club/check_push?af_id=<current real AppsFlyer UID>`.

Если `Latest FCM token sent to config: true`, но `check_push` всё равно возвращает `UNREGISTERED`, клиент отправил актуальный token, а проблема остаётся на backend/Firebase sender side: сервер хранит или использует старый token, не обновляет token для `af_id`, либо отправляет через другой Firebase project.

Если `Config REAL` возвращает negative/`No data`, приложение может открыть Fantic. Это ожидаемое поведение startup flow. Push registration request всё равно должен уйти в `config.php` с `af_id`, `push_token`, `firebase_project_id`, `bundle_id`. Если сервер сохраняет token только при `ok=true`, это backend issue.

## Push tap
1. Отправить реальный push на текущий `af_id`.
2. Проверить notification:
   - используется custom small icon;
   - если payload содержит `image` или `image_url`, notification показывает image.
3. Нажать notification с `data.url`.
4. Ожидаемо: открывается WebView с URL из push.
5. Проверить, что push URL не стал `Last URL` / last successful WebView URL после обычного restart flow.

## Push tap cleartext URL test
1. Получить push через `check_push`.
2. Проверить, что payload содержит `data.url = http://afsub.com/push/`.
3. Нажать notification.
4. Ожидаемо:
   - WebView открывает `http://afsub.com/push/`;
   - ошибки `net::ERR_CLEARTEXT_NOT_PERMITTED` нет;
   - cleartext traffic разрешён только для `afsub.com` и subdomains через `network_security_config.xml`;
   - push URL не сохраняется как `Last URL` / last successful config URL;
   - при следующем обычном запуске startup flow работает как раньше.

Android блокирует HTTP в WebView без network security config. В приложении разрешён cleartext только для тестового push-домена `afsub.com`, глобальный `usesCleartextTraffic=true` не используется.

## Cold start push tap
1. Uninstall app.
2. Install fresh APK.
3. Launch app.
4. В DebugPanel выставить `Config: REAL`, `Attribution: REAL`, `Push token: REAL`.
5. Дождаться `Ready for push test: true`.
6. Закрыть/kill app.
7. Открыть `check_push` URL.
8. Нажать первый notification.
9. Ожидаемо:
   - WebView сразу открывает `http://afsub.com/push/`;
   - Fantic не появляется перед WebView;
   - ошибки `net::ERR_CLEARTEXT_NOT_PERMITTED` нет;
   - `Last launch source: push`;
   - `Push URL consumed: true`;
   - `Push URL persisted as last URL: false`.

## Fantic mode push tap
1. Force app into FANTIC mode через negative real config или `No data`.
2. Закрыть app.
3. Отправить push через `check_push`.
4. Нажать notification.
5. Ожидаемо:
   - WebView открывает push URL несмотря на stored FANTIC mode;
   - push URL не меняет persisted mode на WEBVIEW;
   - следующий обычный запуск без push может снова открыть Fantic, если config всё ещё negative.

## Repeated push tap
1. Отправить push два раза.
2. Нажать первый notification.
3. Нажать второй notification.
4. Ожидаемо: первый и второй tap ведут одинаково — оба открывают WebView с push URL, не только второй.

## Запуск тестовых external Intent напрямую:
(Проверка, что ОС понимает схему. Для WebView кликайте внутри самого приложения).
```bash
adb shell am start -a android.intent.action.VIEW -d "paytmmp://"
adb shell am start -a android.intent.action.VIEW -d "phonepe://"
adb shell am start -a android.intent.action.VIEW -d "bankid://"
```
