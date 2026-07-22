package com.bettermifitness.sync.strava

/**
 * OAuth2 tokens for the Strava API, obtained after the user authorizes this app.
 */
data class StravaCredentials(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val athleteId: Long,
)
