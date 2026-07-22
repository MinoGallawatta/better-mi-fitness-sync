package com.bettermifitness.sync.ui.strava

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.strava.StravaAuth
import com.bettermifitness.sync.strava.StravaCredentialsStore
import com.bettermifitness.sync.strava.StravaSessionManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val REDIRECT_URI = "https://bettermifitnesssync.local/strava-callback"

/**
 * Hosts the OAuth WebView and performs the code → token exchange on success. Set the
 * "Authorization Callback Domain" in your Strava API app settings to [REDIRECT_URI]'s host
 * — it doesn't need to actually resolve, the WebView intercepts before it ever loads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaConnectScreen(onDone: () -> Unit) {
    val stravaAuth = koinInject<StravaAuth>()
    val credentialsStore = koinInject<StravaCredentialsStore>()
    val sessionManager = koinInject<StravaSessionManager>()
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var exchanging by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Connect to Strava") }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                exchanging -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                errorMessage != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(errorMessage ?: "Something went wrong")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { errorMessage = null }) { Text("Try again") }
                    TextButton(onClick = onDone) { Text("Cancel") }
                }
                else -> StravaOAuthWebView(
                    authorizeUrl = stravaAuth.authorizeUrl(REDIRECT_URI),
                    redirectUriPrefix = REDIRECT_URI,
                    onResult = { result ->
                        when (result) {
                            is StravaOAuthResult.Success -> {
                                exchanging = true
                                scope.launch {
                                    try {
                                        val creds = stravaAuth.exchangeCode(result.code)
                                        credentialsStore.saveCredentials(creds)
                                        sessionManager.activate(creds)
                                        onDone()
                                    } catch (e: Exception) {
                                        exchanging = false
                                        errorMessage = e.message ?: "Couldn't connect to Strava"
                                    }
                                }
                            }
                            is StravaOAuthResult.Error -> errorMessage = result.message
                            StravaOAuthResult.Cancelled -> onDone()
                        }
                    },
                )
            }
        }
    }
}
