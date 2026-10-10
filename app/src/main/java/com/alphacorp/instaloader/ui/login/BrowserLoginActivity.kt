package com.alphacorp.instaloader.ui.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.alphacorp.instaloader.R

/** Instagram login in a standalone WebView, following the working InstaLess setup. */
class BrowserLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser_login)

        webView = findViewById(R.id.browser_login_webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.textZoom = 120
        webView.webViewClient = WebViewClient()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        findViewById<Button>(R.id.browser_login_use_account).setOnClickListener {
            returnCookies(cookieManager)
        }

        // InstaLess opens instagram.com directly; keep its navigation behavior.
        webView.loadUrl(INSTAGRAM_URL)
    }

    private fun returnCookies(cookieManager: CookieManager) {
        val cookies = cookieManager.getCookie(INSTAGRAM_URL).orEmpty()
        if (cookies.split(';').none { it.trim().startsWith("sessionid=") }) {
            Toast.makeText(this, R.string.login_cookie_missing, Toast.LENGTH_LONG).show()
            return
        }

        cookieManager.flush()
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_COOKIE_HEADER, cookies)
                .putExtra(EXTRA_USER_AGENT, webView.settings.userAgentString),
        )
        finish()
    }

    companion object {
        const val EXTRA_COOKIE_HEADER = "browser_login.cookie_header"
        const val EXTRA_USER_AGENT = "browser_login.user_agent"
        private const val INSTAGRAM_URL = "https://www.instagram.com"
    }
}
