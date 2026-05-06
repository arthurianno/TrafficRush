package com.games.playNewAdventure.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.games.playNewAdventure.AppConstants
import java.util.Locale

@Composable
fun WebViewScreen(
    url: String,
    activity: Activity,
    onCurrentUrlChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadedUrl by remember { mutableStateOf<String?>(null) }

    BackHandler {
        val currentWebView = webView
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else {
            activity.moveTaskToBack(true)
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.WEBVIEW_SCREEN),
        factory = { viewContext ->
            WebView(viewContext).apply {
                webView = this
                configureWebView(context)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        url?.let(onCurrentUrlChanged)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        return viewContext.handleUrlOverride(request.url)
                    }
                }
                loadedUrl = url
                onCurrentUrlChanged(url)
                loadUrl(url)
            }
        },
        update = { view ->
            webView = view
            if (loadedUrl != url) {
                loadedUrl = url
                onCurrentUrlChanged(url)
                view.loadUrl(url)
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureWebView(context: Context) {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadsImagesAutomatically = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.userAgentString = buildUserAgent(context)
}

private fun buildUserAgent(context: Context): String {
    val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
    return "$defaultUserAgent appid/${AppConstants.APPLICATION_ID} appname/${AppConstants.APP_NAME_WITHOUT_SPACES}"
}

private fun Context.handleUrlOverride(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase(Locale.US)
    if (scheme == "http" || scheme == "https") {
        return false
    }

    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        return true
    }

    return true
}
