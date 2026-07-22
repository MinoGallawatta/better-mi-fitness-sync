package com.bettermifitness.sync.ui.strava

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun StravaOAuthWebView(
    authorizeUrl: String,
    redirectUriPrefix: String,
    onResult: (StravaOAuthResult) -> Unit,
) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val url = request.url.toString()
                    if (!url.startsWith(redirectUriPrefix)) return false
                    val uri = request.url
                    val code = uri.getQueryParameter("code")
                    val error = uri.getQueryParameter("error")
                    when {
                        code != null -> onResult(StravaOAuthResult.Success(code))
                        error == "access_denied" -> onResult(StravaOAuthResult.Cancelled)
                        error != null -> onResult(StravaOAuthResult.Error("Strava returned: $error"))
                        else -> onResult(StravaOAuthResult.Error("Redirect had no authorization code"))
                    }
                    return true
                }
            }
            loadUrl(authorizeUrl)
        }
    })
}
