package com.alphacorp.instaloader.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
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
                enabled = !isImporting && webView?.canGoBack() == true,
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

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    webView = this
                    configureInstagramLoginWebView(viewContext)
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
private fun WebView.configureInstagramLoginWebView(context: android.content.Context) {
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

    webViewClient = object : WebViewClient() {
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
            handler.cancel()
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
