// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class AltaUnlockClient {

    data class UnlockResult(
        val success: Boolean,
        val message: String,
    )

    /** What a resolved pass tells us: the doors it grants and its true expiry (JWT `exp`). */
    data class PassInfo(
        val doors: List<Door>,
        val expiresAt: Long?,
    )

    /**
     * Resolves the pass once and reads both the doors it grants (`entryData`) and its expiry
     * (`exp`) from the same JWT payload.
     */
    fun fetchPassInfo(shortCode: String): Result<PassInfo> {
        return try {
            val token = resolveUnlockToken(shortCode)
            val payload = decodeJwtPayload(token).toString()
            Result.success(PassInfo(Doors.parseFromPayload(payload), PassExpiry.fromJwtExp(payload)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Unlocks a specific door by its entryId; [doorLabel] is used only for the result message. */
    fun unlockDoor(shortCode: String, entryId: Int, doorLabel: String): UnlockResult {
        return try {
            val token = resolveUnlockToken(shortCode)
            val status = postUnlock(token, entryId)
            if (status == HttpURLConnection.HTTP_NO_CONTENT) {
                UnlockResult(true, "$doorLabel unlocked")
            } else {
                UnlockResult(false, "Unlock failed (HTTP $status)")
            }
        } catch (e: Exception) {
            UnlockResult(false, e.message ?: "Unlock failed")
        }
    }

    private fun resolveUnlockToken(shortCode: String): String {
        val response = getJson(AltaConfig.shortUrl(shortCode))
        val fullUrl = response.getJSONObject("data").getString("fullUrl")
        return fullUrl.substringAfter("token=")
    }

    private fun decodeJwtPayload(token: String): JSONObject {
        val payloadSegment = token.split(".")[1]
        val padded = payloadSegment + "=".repeat((4 - payloadSegment.length % 4) % 4)
        val decoded = String(
            Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP),
            StandardCharsets.UTF_8,
        )
        return JSONObject(decoded)
    }

    private fun postUnlock(token: String, entryId: Int): Int {
        val body = JSONObject().put("entryId", entryId).toString()
        return postJson(AltaConfig.unlockUrl(token), body)
    }

    private fun getJson(url: String): JSONObject {
        val connection = openConnection(url, "GET")
        return readJsonResponse(connection)
    }

    private fun postJson(url: String, body: String): Int {
        val connection = openConnection(url, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { stream ->
            stream.write(body.toByteArray(StandardCharsets.UTF_8))
        }
        val responseCode = connection.responseCode
        connection.disconnect()
        return responseCode
    }

    private fun readJsonResponse(connection: HttpURLConnection): JSONObject {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Request failed (HTTP ${connection.responseCode}): $text")
        }
        return JSONObject(text)
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        return connection
    }
}
