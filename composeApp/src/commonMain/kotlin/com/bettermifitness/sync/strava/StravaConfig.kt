package com.bettermifitness.sync.strava

/**
 * Strava API app credentials for this build, set once from Android BuildConfig
 * (sourced from a gitignored local.properties) before initKoin().
 *
 * OAuth2's authorization-code exchange requires a client_secret, which is only safe to
 * ship in the app because this is a personal, non-distributed build — never repeat this
 * for an app meant to be published or shared with other users.
 */
object StravaConfig {
    lateinit var clientId: String
    lateinit var clientSecret: String

    val isConfigured: Boolean
        get() = ::clientId.isInitialized && ::clientSecret.isInitialized &&
            clientId.isNotBlank() && clientSecret.isNotBlank()
}
