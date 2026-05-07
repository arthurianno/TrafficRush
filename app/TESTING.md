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
3. Установить debug APK и запустить приложение.
4. В DebugPanel прокрутить до секции "Deep Link Params" и проверить:
   - `deep_link_value`: deep_link_test
   - `deep_link_sub1`: deep_test_sub1
   - `has required deeplink params`: true
   - `last config contained deep_link_value`: true
   - `last config contained any deep_link_sub`: true

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

## Запуск тестовых external Intent напрямую:
(Проверка, что ОС понимает схему. Для WebView кликайте внутри самого приложения).
```bash
adb shell am start -a android.intent.action.VIEW -d "paytmmp://"
adb shell am start -a android.intent.action.VIEW -d "phonepe://"
adb shell am start -a android.intent.action.VIEW -d "bankid://"
```
