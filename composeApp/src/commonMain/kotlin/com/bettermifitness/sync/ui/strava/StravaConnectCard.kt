package com.bettermifitness.sync.ui.strava

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.strava.StravaCredentialsStore
import com.bettermifitness.sync.strava.StravaSessionManager
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.theme.BrandSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun StravaConnectCard(onConnectClick: () -> Unit) {
    val credentialsStore = koinInject<StravaCredentialsStore>()
    val sessionManager = koinInject<StravaSessionManager>()
    val isConnected by credentialsStore.isConnected.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BrandShapes.Card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(BrandSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Strava",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (isConnected) {
                    "Connected — new workouts are pushed to Strava with heart rate."
                } else {
                    "Push synced workouts to Strava, including heart rate."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (isConnected) {
                OutlinedButton(
                    onClick = { scope.launch { sessionManager.disconnect() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnectClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect to Strava")
                }
            }
        }
    }
}
