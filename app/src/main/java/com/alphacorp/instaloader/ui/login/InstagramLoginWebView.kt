package com.alphacorp.instaloader.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.alphacorp.instaloader.R
import com.alphacorp.instaloader.util.InstagramLoginUrlPolicy

@Composable
fun InstagramLoginWebView(
    isImporting: Boolean,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onUseThisAccount: (cookieHeader: String, userAgent: String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canNavigateBack by remember { mutableStateOf(false) }
    var pageState by remember { mutableStateOf(InstagramPageState()) }

    BackHandler {
        val currentView = webView
        if (currentView?.canGoBack() == true) {
            currentView.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(webView) {
        val viewToDestroy = webView
        onDispose {
            viewToDestroy?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { webView?.takeIf { it.canGoBack() }?.goBack() },
                enabled = !isImporting && canNavigateBack,
            ) {
                Text(stringResource(R.string.login_webview_back))
            }
            OutlinedButton(
                onClick = { webView?.reload() },
                enabled = !isImporting,
            ) {
                Text(stringResource(R.string.login_webview_reload))
            }
            OutlinedButton(onClick = onClose, enabled = !isImporting) {
                Text(stringResource(R.string.login_webview_close))
            }
        }

        Text(
            text = stringResource(R.string.login_webview_instruction),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = pageState.errorMessage ?: if (pageState.isLoading) {
                stringResource(
                    R.string.login_webview_status_loading,
                    pageState.host,
                    pageState.progressPercent,
                )
            } else {
                stringResource(R.string.login_webview_status_loaded, pageState.host)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (pageState.errorMessage == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (pageState.isLoading) {
            LinearProgressIndicator(
                progress = { pageState.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    webView = this
                    configureInstagramLoginWebView(
                        context = viewContext,
                        onNavigationStateChanged = { canNavigateBack = it },
                        onPageStateChanged = { pageState = it },
                    )
                    loadUrl(INSTAGRAM_LOGIN_URL)
                }
            },
        )

        Button(
            onClick = {
                val currentView = webView ?: return@Button
                val cookieHeader = CookieManager.getInstance().getCookie(INSTAGRAM_ORIGIN).orEmpty()
                onUseThisAccount(cookieHeader, currentView.settings.userAgentString)
            },
            enabled = !isImporting && webView != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    if (isImporting) R.string.login_webview_verifying else R.string.login_use_this_account,
                ),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureInstagramLoginWebView(
    context: android.content.Context,
    onNavigationStateChanged: (canGoBack: Boolean) -> Unit,
    onPageStateChanged: (InstagramPageState) -> Unit,
) {
    CookieManager.getInstance().setAcceptCookie(true)
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = true
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WebView.startSafeBrowsing(context.applicationContext) { }
    }

    var mainFrameUrl = INSTAGRAM_LOGIN_URL
    var mainFrameLoadFailed = false

    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            mainFrameUrl = url
            mainFrameLoadFailed = false
            onNavigationStateChanged(view.canGoBack())
            onPageStateChanged(
                InstagramPageState(
                    host = urlHost(url),
                    isLoading = true,
                ),
            )
        }

        override fun onPageFinished(view: WebView, url: String) {
            onNavigationStateChanged(view.canGoBack())
            if (!mainFrameLoadFailed) {
                onPageStateChanged(
                    InstagramPageState(
                        host = urlHost(url),
                        progressPercent = 100,
                        isLoading = false,
                    ),
                )
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                mainFrameLoadFailed = true
                onPageStateChanged(
                    InstagramPageState(
                        host = urlHost(request.url.toString()),
                        isLoading = false,
                        errorMessage = view.context.getString(
                            R.string.login_webview_status_load_error,
                            error.errorCode,
                            error.description.toString().take(MAX_WEB_ERROR_LENGTH),
                        ),
                    ),
                )
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame) {
                mainFrameLoadFailed = true
                onPageStateChanged(
                    InstagramPageState(
                        host = urlHost(request.url.toString()),
                        isLoading = false,
                        errorMessage = view.context.getString(
                            R.string.login_webview_status_http_error,
                            errorResponse.statusCode,
                            urlHost(request.url.toString()),
                        ),
                    ),
                )
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            if (request.isForMainFrame) {
                handleNavigation(view.context, request.url.toString())
            } else {
                false
            }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: android.net.http.SslError,
        ) {
            if (samePageUrl(error.url, mainFrameUrl) || samePageUrl(error.url, view.url.orEmpty())) {
                mainFrameLoadFailed = true
                onPageStateChanged(
                    InstagramPageState(
                        host = urlHost(error.url),
                        isLoading = false,
                        errorMessage = view.context.getString(
                            R.string.login_webview_status_ssl_error,
                            error.primaryError,
                            urlHost(error.url),
                        ),
                    ),
                )
            }
            handler.cancel()
        }
    }

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (!mainFrameLoadFailed) {
                onPageStateChanged(
                    InstagramPageState(
                        host = urlHost(view.url.orEmpty()),
                        progressPercent = newProgress,
                        isLoading = newProgress < 100,
                    ),
                )
            }
        }
    }
}

private fun handleNavigation(context: android.content.Context, url: String): Boolean =
    when (InstagramLoginUrlPolicy.classify(url)) {
        InstagramLoginUrlPolicy.Destination.WEBVIEW -> false
        InstagramLoginUrlPolicy.Destination.EXTERNAL_HTTPS -> {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            true
        }
        InstagramLoginUrlPolicy.Destination.BLOCK -> true
    }

private const val INSTAGRAM_ORIGIN = "https://www.instagram.com"
private const val INSTAGRAM_LOGIN_URL = "$INSTAGRAM_ORIGIN/accounts/login/"
private const val MAX_WEB_ERROR_LENGTH = 120

private data class InstagramPageState(
    val host: String = "www.instagram.com",
    val progressPercent: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

private fun urlHost(url: String): String = Uri.parse(url).host ?: "Instagram"

private fun samePageUrl(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return false
    val firstUri = Uri.parse(first)
    val secondUri = Uri.parse(second)
    return firstUri.scheme.equals(secondUri.scheme, ignoreCase = true) &&
        firstUri.host.equals(secondUri.host, ignoreCase = true) &&
        normalizedPort(firstUri) == normalizedPort(secondUri) &&
        firstUri.encodedPath == secondUri.encodedPath &&
        firstUri.encodedQuery == secondUri.encodedQuery
}

private fun normalizedPort(uri: Uri): Int = when {
    uri.port != -1 -> uri.port
    uri.scheme.equals("https", ignoreCase = true) -> 443
    uri.scheme.equals("http", ignoreCase = true) -> 80
    else -> -1
}
