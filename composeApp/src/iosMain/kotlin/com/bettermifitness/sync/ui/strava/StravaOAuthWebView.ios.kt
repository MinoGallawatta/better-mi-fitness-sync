package com.bettermifitness.sync.ui.strava

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Strava sync is Android-only for v1 (see plan) — this stub is reachable only if a user
 * taps "Connect to Strava" on iOS, and just explains that it isn't supported yet.
 */
@Composable
actual fun StravaOAuthWebView(
    authorizeUrl: String,
    redirectUriPrefix: String,
    onResult: (StravaOAuthResult) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Strava sync isn't available on iOS yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
