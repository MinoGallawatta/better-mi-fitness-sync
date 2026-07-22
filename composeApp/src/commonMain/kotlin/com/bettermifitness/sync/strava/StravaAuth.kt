package com.bettermifitness.sync.strava

import com.mifitness.miclient.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Strava OAuth2 (authorization-code + refresh) against strava.com's token endpoint.
 *
 * No PKCE — Strava requires a client_secret for every grant, which is why this feature is
 * scoped to a personal, non-distributed build (see [StravaConfig]).
 */
class StravaAuth(
    private val httpClient: HttpClient = createPlatformHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun authorizeUrl(
        redirectUri: String,
        scope: String = "activity:write,activity:read_all",
    ): String {
        val params = listOf(
            "client_id" to StravaConfig.clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "approval_prompt" to "auto",
            "scope" to scope,
        ).formUrlEncode()
        return "https://www.strava.com/oauth/authorize?$params"
    }

    suspend fun exchangeCode(code: String): StravaCredentials {
        val response = tokenRequest(
            "grant_type" to "authorization_code",
            "code" to code,
        )
        val athleteId = response.athlete?.id
            ?: throw StravaApiException.Unexpected("Strava token exchange did not return an athlete id")
        return response.toCredentials(athleteId)
    }

    /** Refresh grant responses don't include the athlete object, so it's passed through. */
    suspend fun refresh(refreshToken: String, athleteId: Long): StravaCredentials {
        val response = tokenRequest(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        )
        return response.toCredentials(athleteId)
    }

    /** Best-effort revoke; failures are ignored by callers (see StravaSessionManager.disconnect). */
    suspend fun deauthorize(accessToken: String) {
        httpClient.post("https://www.strava.com/oauth/deauthorize") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("access_token" to accessToken).formUrlEncode())
        }
    }

    private suspend fun tokenRequest(vararg extra: Pair<String, String>): TokenResponse {
        val body = (
            listOf(
                "client_id" to StravaConfig.clientId,
                "client_secret" to StravaConfig.clientSecret,
            ) + extra
            ).formUrlEncode()

        val response = try {
            httpClient.post("https://www.strava.com/oauth/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body)
            }
        } catch (e: Exception) {
            throw StravaApiException.Network(e.message ?: "Network error", e)
        }

        val status = response.status.value
        val text = response.bodyAsText()
        when {
            status == 401 || status == 403 ->
                throw StravaApiException.AuthExpired("Strava rejected the token request (HTTP $status)")
            status == 429 ->
                throw StravaApiException.RateLimited(message = "Strava rate limited the token request")
            status !in 200..299 ->
                throw StravaApiException.Server(status, "Strava token HTTP $status: ${text.take(200)}")
        }

        return try {
            json.decodeFromString(TokenResponse.serializer(), text)
        } catch (e: Exception) {
            throw StravaApiException.Unexpected("Invalid JSON from Strava token endpoint", e)
        }
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_at") val expiresAt: Long,
        val athlete: Athlete? = null,
    ) {
        fun toCredentials(athleteId: Long) = StravaCredentials(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSec = expiresAt,
            athleteId = athleteId,
        )
    }

    @Serializable
    private data class Athlete(val id: Long)
}
