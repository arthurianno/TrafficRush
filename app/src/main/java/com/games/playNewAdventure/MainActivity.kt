package com.games.playNewAdventure

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import com.games.playNewAdventure.data.AppStorage
import com.games.playNewAdventure.service.AndroidDeviceDataProvider
import com.games.playNewAdventure.service.AndroidNetworkChecker
import com.games.playNewAdventure.service.AppsFlyerAttributionService
import com.games.playNewAdventure.service.FirebasePushService
import com.games.playNewAdventure.service.MockAttributionService
import com.games.playNewAdventure.service.MockConfigService
import com.games.playNewAdventure.service.MockPushService
import com.games.playNewAdventure.service.RemoteConfigService
import com.games.playNewAdventure.service.SwitchingAttributionService
import com.games.playNewAdventure.service.SwitchingConfigService
import com.games.playNewAdventure.service.SwitchingPushTokenService
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.domain.AttributionProvider
import com.games.playNewAdventure.startup.domain.AttributionProviderMode
import com.games.playNewAdventure.startup.domain.ConfigProvider
import com.games.playNewAdventure.startup.domain.ConfigProviderMode
import com.games.playNewAdventure.startup.domain.MockConfigScenario
import com.games.playNewAdventure.startup.domain.PushTokenProvider
import com.games.playNewAdventure.startup.domain.PushTokenProviderMode
import com.games.playNewAdventure.ui.AndroidNotificationPermissionRequester
import com.games.playNewAdventure.ui.NotificationPermissionRequester
import com.games.playNewAdventure.ui.StartupHost
import com.games.playNewAdventure.ui.theme.TrafficRushTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private var webFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var webCameraCaptureUri: Uri? = null

    private val appStorage by lazy {
        AppStorage(applicationContext)
    }

    private val attributionProvider: AttributionProvider by lazy {
        if (BuildConfig.DEBUG) {
            SwitchingAttributionService(
                modeProvider = { appStorage.attributionProviderMode },
                mockAttributionService = MockAttributionService(),
                realAttributionService = AppsFlyerAttributionService(applicationContext)
            )
        } else {
            AppsFlyerAttributionService(applicationContext)
        }
    }

    private val pushTokenProvider: PushTokenProvider by lazy {
        if (BuildConfig.DEBUG) {
            SwitchingPushTokenService(
                modeProvider = { appStorage.pushTokenProviderMode },
                mockPushService = MockPushService(),
                realPushService = FirebasePushService()
            )
        } else {
            FirebasePushService()
        }
    }

    private val configProvider: ConfigProvider by lazy {
        val remoteConfigService = RemoteConfigService()
        if (BuildConfig.DEBUG) {
            SwitchingConfigService(
                modeProvider = { appStorage.configProviderMode },
                mockConfigService = MockConfigService { appStorage.mockConfigScenario },
                remoteConfigService = remoteConfigService
            )
        } else {
            remoteConfigService
        }
    }

    private val startupController by lazy {
        AppStartupController(
            storage = appStorage,
            networkStatusProvider = AndroidNetworkChecker(applicationContext),
            attributionProvider = attributionProvider,
            pushTokenProvider = pushTokenProvider,
            configProvider = configProvider,
            deviceDataProvider = AndroidDeviceDataProvider(),
            debugLogger = { message ->
                if (BuildConfig.DEBUG) {
                    Log.d("AppStartupController", message)
                }
            }
        )
    }

    private val notificationPermissionRequester: NotificationPermissionRequester =
        AndroidNotificationPermissionRequester(
            activity = this,
            onPermissionResult = { granted ->
                startupController.recordPushPermissionResult(granted)
            }
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyDebugIntentOverrides(intent)

        setContent {
            TrafficRushTheme {
                StartupHost(
                    activity = this,
                    startupController = startupController,
                    initialWebViewUrl = intent.pushWebViewUrlOrNull(),
                    onShowWebFileChooser = ::openWebFileChooser,
                    onPushPermissionAccepted = { onComplete ->
                        notificationPermissionRequester.requestNotificationPermission(onComplete)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyDebugIntentOverrides(intent) || intent.pushWebViewUrlOrNull() != null) {
            recreate()
        }
    }

    @Deprecated("Kept for WebView file chooser compatibility without adding new dependencies.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_WEB_FILE_CHOOSER) {
            return
        }

        val result = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            ?: parseWebFileChooserResult(resultCode, data)
        webFilePathCallback?.onReceiveValue(result)
        webFilePathCallback = null
        webCameraCaptureUri = null
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        notificationPermissionRequester.handlePermissionResult(
            requestCode = requestCode,
            grantResults = grantResults
        )
    }

    private companion object {
        const val REQUEST_WEB_FILE_CHOOSER = 20_400
        const val EXTRA_DEBUG_RESET = "debug_reset"
        const val EXTRA_DEBUG_CONFIG_SOURCE = "debug_config_source"
        const val EXTRA_DEBUG_MOCK_SCENARIO = "debug_mock_scenario"
        const val EXTRA_DEBUG_ATTRIBUTION_SOURCE = "debug_attribution_source"
        const val EXTRA_DEBUG_PUSH_SOURCE = "debug_push_source"
    }

    private fun openWebFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        webFilePathCallback?.onReceiveValue(null)
        webFilePathCallback = callback
        webCameraCaptureUri = null

        return runCatching {
            startActivityForResult(createWebFileChooserIntent(params), REQUEST_WEB_FILE_CHOOSER)
            true
        }.getOrElse {
            webFilePathCallback?.onReceiveValue(null)
            webFilePathCallback = null
            webCameraCaptureUri = null
            false
        }
    }

    private fun createWebFileChooserIntent(params: WebChromeClient.FileChooserParams): Intent {
        val pickerIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = params.acceptedMimeType()
            putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val cameraIntent = if (params.acceptsImage()) {
            createCameraCaptureIntent()
        } else {
            null
        }

        return Intent.createChooser(pickerIntent, null).apply {
            cameraIntent?.let {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it))
            }
        }
    }

    private fun createCameraCaptureIntent(): Intent? {
        val photoFile = runCatching {
            File.createTempFile(
                "web_upload_",
                ".jpg",
                getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            )
        }.getOrNull() ?: return null

        val photoUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            photoFile
        )

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        val cameraActivities = packageManager.queryIntentActivities(cameraIntent, 0)
        if (cameraActivities.isEmpty()) {
            return null
        }

        cameraActivities.forEach { resolveInfo ->
            grantUriPermission(
                resolveInfo.activityInfo.packageName,
                photoUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        webCameraCaptureUri = photoUri
        return cameraIntent
    }

    private fun parseWebFileChooserResult(
        resultCode: Int,
        data: Intent?
    ): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK) {
            return null
        }

        data?.clipData?.let { clipData ->
            return Array(clipData.itemCount) { index ->
                clipData.getItemAt(index).uri
            }
        }

        data?.data?.let { uri ->
            return arrayOf(uri)
        }

        return webCameraCaptureUri?.let { arrayOf(it) }
    }

    private fun WebChromeClient.FileChooserParams.acceptedMimeType(): String {
        val normalizedTypes = acceptTypes
            .mapNotNull { rawType ->
                rawType.substringBefore(";").trim().takeIf { it.isNotBlank() }
            }

        return when {
            normalizedTypes.isEmpty() -> "*/*"
            normalizedTypes.all { it.startsWith("image/") } -> "image/*"
            normalizedTypes.size == 1 -> normalizedTypes.first()
            else -> "*/*"
        }
    }

    private fun WebChromeClient.FileChooserParams.acceptsImage(): Boolean {
        return acceptTypes.any { rawType ->
            val normalizedType = rawType.substringBefore(";").trim()
            normalizedType == "image/*" || normalizedType.startsWith("image/")
        }
    }

    private fun Intent.pushWebViewUrlOrNull(): String? {
        return getStringExtra(AppConstants.EXTRA_PUSH_URL)?.takeIf { it.isNotBlank() }
    }

    private fun applyDebugIntentOverrides(intent: Intent?): Boolean {
        if (!BuildConfig.DEBUG || intent == null) {
            return false
        }

        var changed = false
        if (intent.getBooleanExtra(EXTRA_DEBUG_RESET, false)) {
            appStorage.resetLocalState()
            changed = true
        }

        intent.getStringExtra(EXTRA_DEBUG_CONFIG_SOURCE)?.let { rawMode ->
            runCatching { ConfigProviderMode.valueOf(rawMode.uppercase()) }.getOrNull()
                ?.let { mode ->
                    appStorage.configProviderMode = mode
                    changed = true
                }
        }

        intent.getStringExtra(EXTRA_DEBUG_MOCK_SCENARIO)?.let { rawScenario ->
            runCatching { MockConfigScenario.valueOf(rawScenario.uppercase()) }.getOrNull()
                ?.let { scenario ->
                    appStorage.mockConfigScenario = scenario
                    changed = true
                }
        }

        intent.getStringExtra(EXTRA_DEBUG_ATTRIBUTION_SOURCE)?.let { rawMode ->
            runCatching { AttributionProviderMode.valueOf(rawMode.uppercase()) }.getOrNull()
                ?.let { mode ->
                    appStorage.attributionProviderMode = mode
                    changed = true
                }
        }

        intent.getStringExtra(EXTRA_DEBUG_PUSH_SOURCE)?.let { rawMode ->
            runCatching { PushTokenProviderMode.valueOf(rawMode.uppercase()) }.getOrNull()
                ?.let { mode ->
                    appStorage.pushTokenProviderMode = mode
                    changed = true
                }
        }

        return changed
    }
}
