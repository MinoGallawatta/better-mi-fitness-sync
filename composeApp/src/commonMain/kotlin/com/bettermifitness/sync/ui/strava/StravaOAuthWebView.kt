package com.bettermifitness.sync.ui.strava

import androidx.compose.runtime.Composable

sealed class StravaOAuthResult {
    data class Success(val code: String) : StravaOAuthResult()
    data class Error(val message: String) : StravaOAuthResult()
    data object Cancelled : StravaOAuthResult()
}

/**
 * Renders Strava's OAuth authorize page and intercepts the redirect to [redirectUriPrefix]
 * before it loads, extracting the `code`/`error` query params — no backend server needed.
 */
@Composable
expect fun StravaOAuthWebView(
    authorizeUrl: String,
    redirectUriPrefix: String,
    onResult: (StravaOAuthResult) -> Unit,
)
