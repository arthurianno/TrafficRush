package com.games.playNewAdventure.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.BuildConfig
import java.util.Locale

@Composable
fun WebViewScreen(
    url: String,
    activity: Activity,
    modifier: Modifier = Modifier,
    onShowFileChooser: (
        ValueCallback<Array<Uri>>,
        WebChromeClient.FileChooserParams
    ) -> Boolean = { _, _ -> false },
    onCurrentUrlChanged: (String) -> Unit = {}
) {

    if (LocalInspectionMode.current) {
        WebViewPreviewPlaceholder(
            url = url,
            modifier = modifier
        )
        return
    }

    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }

    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    val webViewStateBundle = rememberSaveable(
        saver = Saver<android.os.Bundle, android.os.Bundle>(
            save = { 
                logDebug("save WebView state called")
                webView?.saveState(it)
                it 
            },
            restore = { it }
        )
    ) { android.os.Bundle() }
    var loadedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showFallbackError by remember { mutableStateOf(false) }
    var fallbackUrl by remember { mutableStateOf<String?>(null) }

    BackHandler {
        val currentWebView = webView
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else {
            activity.moveTaskToBack(true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .testTag(UiTestTags.WEBVIEW_SCREEN)
    ) {
        if (showFallbackError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Page Load Error",
                    color = Color.Black,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Too many redirects occurred. The page could not be loaded.",
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.Button(onClick = {
                    showFallbackError = false
                    fallbackUrl?.let { urlToLoad ->
                        webView?.loadUrlOrOpenExternally(context, urlToLoad)
                    }
                }) {
                    Text("Retry")
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize().then(if (showFallbackError) Modifier.height(0.dp) else Modifier),
            factory = { viewContext ->
                var lastStartedUrl: String? = null
                var lastFinishedUrl: String? = null
                var redirectRetryCount = 0

                WebView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webView = this
                    configureWebView(context)
                    webChromeClient = createWebChromeClient(onShowFileChooser)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            logDebug("onPageStarted: $url, lastStartedUrl=$lastStartedUrl, lastFinishedUrl=$lastFinishedUrl")
                            lastStartedUrl = url
                            url?.let(onCurrentUrlChanged)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            logDebug("onPageFinished: $url")
                            lastFinishedUrl = url
                            redirectRetryCount = 0
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            val code = error.errorCode
                            val desc = error.description?.toString() ?: ""
                            logDebug(
                                "onReceivedError: url=${request.url}, mainFrame=${request.isForMainFrame}, " +
                                    "code=$code, description=$desc"
                            )
                            
                            val isUnsupportedScheme = code == WebViewClient.ERROR_UNSUPPORTED_SCHEME || 
                                                      desc.contains("ERR_UNKNOWN_URL_SCHEME", ignoreCase = true)
                            if (isUnsupportedScheme) {
                                view.stopLoading()
                                if (view.canGoBack()) {
                                    view.goBack()
                                }
                                val requestUrl = request.url
                                if (requestUrl != null && !requestUrl.isHttpOrHttps()) {
                                    viewContext.handleUrlOverride(requestUrl)
                                }
                                return
                            }

                            val isTooManyRedirects = code == WebViewClient.ERROR_REDIRECT_LOOP || 
                                                     desc.contains("ERR_TOO_MANY_REDIRECTS", ignoreCase = true)
                            
                            if (isTooManyRedirects && request.isForMainFrame) {
                                if (redirectRetryCount < 3) {
                                    redirectRetryCount++
                                    val retryUrl = request.url.toString()
                                    logDebug("Retrying redirect URL: $retryUrl (attempt $redirectRetryCount)")
                                    view.loadUrl(retryUrl)
                                } else {
                                    logDebug("Exceeded max redirect retries. Showing fallback.")
                                    fallbackUrl = request.url.toString()
                                    showFallbackError = true
                                    
                                    // Revert last successful URL to avoid saving the broken redirect
                                    lastFinishedUrl?.let { onCurrentUrlChanged(it) }
                                }
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse
                        ) {
                            logDebug(
                                "onReceivedHttpError: url=${request.url}, mainFrame=${request.isForMainFrame}, " +
                                    "status=${errorResponse.statusCode}, reason=${errorResponse.reasonPhrase}"
                            )
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            logDebug("shouldOverrideUrlLoading: ${request.url}")
                            val uri = request.url
                            if (uri.isHttpOrHttps()) {
                                return false
                            }
                            return viewContext.handleUrlOverride(uri)
                        }
                    }
                    if (!webViewStateBundle.isEmpty) {
                        logDebug("restore WebView state called.")
                        restoreState(webViewStateBundle)
                        val restoredHistoryIndex = copyBackForwardList().currentIndex
                        logDebug("restored history size: ${copyBackForwardList().size}, index: $restoredHistoryIndex")
                        logDebug("loadUrl was skipped due to restoreState")
                    } else {
                        loadedUrl = url
                        logDebug("Restoring/Loading initial URL: $url")
                        onCurrentUrlChanged(url)
                        loadUrlOrOpenExternally(viewContext, url)
                    }
                }
            },
            update = { view ->
                webView = view
                if (loadedUrl != url && !showFallbackError) {
                    loadedUrl = url
                    logDebug("Updating URL due to recomposition: $url")
                    onCurrentUrlChanged(url)
                    view.loadUrlOrOpenExternally(context, url)
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
private fun WebViewPreviewPlaceholder(
    url: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(Color(0xFFEAEAEA))
            .testTag(UiTestTags.WEBVIEW_SCREEN),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WebView Preview",
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = url,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun WebViewPreviewPlaceholderPreview() {
    WebViewPreviewPlaceholder(
        url = "https://web.team-s.club/"
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureWebView(context: Context) {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    // Required by the configured WebView content: login/session cookies, JS UI,
    // DOM storage, and autoplay media must keep current product behavior.
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadsImagesAutomatically = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    settings.textZoom = WEBVIEW_TEXT_ZOOM_PERCENT
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    settings.userAgentString = buildUserAgent(context)
}

private fun createWebChromeClient(
    onShowFileChooser: (
        ValueCallback<Array<Uri>>,
        WebChromeClient.FileChooserParams
    ) -> Boolean
): WebChromeClient {
    return object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            val allowed = request.resources
                .filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                .toTypedArray()

            if (allowed.isNotEmpty()) {
                logDebug("onPermissionRequest grant: ${allowed.joinToString()}")
                request.grant(allowed)
            } else {
                logDebug("onPermissionRequest deny: ${request.resources.joinToString()}")
                request.deny()
            }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            logDebug(
                "${consoleMessage.message()} -- " +
                    "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
            )
            return true
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            logDebug(
                "onShowFileChooser: accept=${fileChooserParams.acceptTypes.joinToString()}, " +
                    "capture=${fileChooserParams.isCaptureEnabled}, mode=${fileChooserParams.mode}"
            )
            val handled = onShowFileChooser(filePathCallback, fileChooserParams)
            if (!handled) {
                filePathCallback.onReceiveValue(null)
            }
            return handled
        }
    }
}

private fun WebView.loadUrlOrOpenExternally(context: Context, rawUrl: String) {
    val uri = rawUrl.toUri()
    if (uri.isHttpOrHttps()) {
        // TODO(P3): enforce WEBVIEW_HOST_ALLOWLIST once the config contract
        // guarantees whether campaign or partner domains can be returned.
        if (ENFORCE_WEBVIEW_HOST_ALLOWLIST && !uri.isKnownWebViewHost()) {
            context.handleUrlOverride(uri)
            return
        }
        loadUrl(rawUrl)
    } else {
        context.handleUrlOverride(uri)
    }
}

private fun buildUserAgent(context: Context): String {
    val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
    val chromeCompatibleUserAgent = defaultUserAgent.toChromeCompatibleUserAgent()
    val shouldAppendAppTokens = !BuildConfig.DEBUG || DEBUG_APPEND_APP_USER_AGENT
    val userAgent = if (shouldAppendAppTokens) {
        "$chromeCompatibleUserAgent appid/${AppConstants.APPLICATION_ID} appname/${AppConstants.APP_NAME_WITHOUT_SPACES}"
    } else {
        chromeCompatibleUserAgent
    }

    logDebug(
        "User-Agent mode=${if (shouldAppendAppTokens) "chrome-compatible+app" else "chrome-compatible"}; " +
            "value=$userAgent"
    )
    return userAgent
}

private fun String.toChromeCompatibleUserAgent(): String {
    return replace("; wv", "")
        .replace("Version/4.0 ", "")
}

private fun Context.handleUrlOverride(uri: Uri): Boolean {
    if (uri.isHttpOrHttps()) {
        return false
    }

    val uriString = uri.toString()
    val intent = if (uriString.startsWith("intent://", ignoreCase = true)) {
        try {
            Intent.parseUri(uriString, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            null
        }
    } else {
        Intent(Intent.ACTION_VIEW, uri)
    }

    if (intent == null) return true

    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        startActivity(intent)
        logDebug("External URL handled successfully: $uri")
    } catch (_: ActivityNotFoundException) {
        logDebug("ActivityNotFoundException for intent: $uri")
        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
        if (fallbackUrl != null) {
            val fallbackUri = Uri.parse(fallbackUrl)
            if (fallbackUri.isHttpOrHttps()) {
                logDebug("Using fallback URL: $fallbackUrl")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                if (this !is Activity) {
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { startActivity(fallbackIntent) }
            }
        }
        return true
    }

    return true
}

private fun Uri.isHttpOrHttps(): Boolean {
    val scheme = scheme?.lowercase(Locale.US)
    return scheme == "http" || scheme == "https"
}

private fun Uri.isKnownWebViewHost(): Boolean {
    val normalizedHost = host?.lowercase(Locale.US) ?: return false
    return normalizedHost in WEBVIEW_HOST_ALLOWLIST
}

private const val ENFORCE_WEBVIEW_HOST_ALLOWLIST = false
private const val DEBUG_APPEND_APP_USER_AGENT = true
private const val WEBVIEW_TEXT_ZOOM_PERCENT = 100
private const val WEBVIEW_LOG_TAG = "TrafficRushWebView"

private val WEBVIEW_HOST_ALLOWLIST = setOf(
    "web.team-s.club",
    "traficruush.com"
)

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(WEBVIEW_LOG_TAG, message)
    }
}
