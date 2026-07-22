package com.bettermifitness.sync.strava

import com.mifitness.miclient.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class StravaUploadStatus(
    val uploadId: Long,
    val externalId: String?,
    val status: String?,
    val error: String?,
    val activityId: Long?,
)

/**
 * Strava's activity file upload: `POST /uploads` (multipart), then poll `GET /uploads/{id}`
 * until it resolves to an activity_id (or an error). Stateless — callers pass a fresh access
 * token per call (see [StravaWorkoutSyncer] for the auth-retry wrapper).
 */
class StravaUploadApi(
    private val httpClient: HttpClient = createPlatformHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun uploadTcx(
        accessToken: String,
        tcxXml: String,
        externalId: String,
        name: String?,
    ): StravaUploadStatus {
        val response = try {
            httpClient.submitFormWithBinaryData(
                url = "$BASE/uploads",
                formData = formData {
                    append("data_type", "tcx")
                    append("external_id", externalId)
                    if (!name.isNullOrBlank()) append("name", name)
                    append(
                        "file",
                        tcxXml.encodeToByteArray(),
                        Headers.build {
                            append(HttpHeaders.ContentType, "application/xml")
                            append(HttpHeaders.ContentDisposition, "filename=\"$externalId.tcx\"")
                        },
                    )
                },
            ) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        } catch (e: Exception) {
            throw mapTransportException(e)
        }
        return parseUploadResponse(response)
    }

    suspend fun pollUpload(accessToken: String, uploadId: Long): StravaUploadStatus {
        val response = try {
            httpClient.get("$BASE/uploads/$uploadId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        } catch (e: Exception) {
            throw mapTransportException(e)
        }
        return parseUploadResponse(response)
    }

    private suspend fun parseUploadResponse(response: HttpResponse): StravaUploadStatus {
        val statusCode = response.status.value
        val body = response.bodyAsText()
        when {
            statusCode == 401 ->
                throw StravaApiException.AuthExpired("Strava rejected the request (HTTP 401)")
            statusCode == 429 -> {
                val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toIntOrNull()
                throw StravaApiException.RateLimited(
                    retryAfterSec = retryAfter,
                    message = "Strava rate limited the upload",
                )
            }
            statusCode !in 200..299 ->
                throw StravaApiException.Server(statusCode, "Strava upload HTTP $statusCode: ${body.take(200)}")
        }

        val dto = try {
            json.decodeFromString(UploadResponseDto.serializer(), body)
        } catch (e: Exception) {
            throw StravaApiException.Unexpected("Invalid JSON from Strava uploads endpoint", e)
        }
        return StravaUploadStatus(
            uploadId = dto.id,
            externalId = dto.externalId,
            status = dto.status,
            error = dto.error,
            activityId = dto.activityId,
        )
    }

    private fun mapTransportException(e: Exception): StravaApiException {
        if (e is StravaApiException) return e
        val name = e::class.simpleName.orEmpty()
        val msg = e.message.orEmpty()
        val looksNetwork = name.contains("Timeout", ignoreCase = true) ||
            name.contains("UnknownHost", ignoreCase = true) ||
            name.contains("Socket", ignoreCase = true) ||
            name.contains("Unresolved", ignoreCase = true) ||
            name.contains("Connect", ignoreCase = true) ||
            msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true)
        return if (looksNetwork) {
            StravaApiException.Network(msg.ifBlank { "Network error" }, e)
        } else {
            StravaApiException.Unexpected(msg.ifBlank { name.ifBlank { "Request failed" } }, e)
        }
    }

    @Serializable
    private data class UploadResponseDto(
        val id: Long,
        @SerialName("external_id") val externalId: String? = null,
        val error: String? = null,
        val status: String? = null,
        @SerialName("activity_id") val activityId: Long? = null,
    )

    companion object {
        private const val BASE = "https://www.strava.com/api/v3"
    }
}
