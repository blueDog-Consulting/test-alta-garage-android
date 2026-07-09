// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import android.content.Context
import android.net.Uri

class UnlockConfigStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Snapshot of the saved pass and what we know about its expiry. */
    data class PassStatus(
        val shortCode: String,
        val expiresAt: Long?,
        val expirySource: String,
    )

    /** Outcome of a save attempt. [Saved.alreadyExpired] lets the UI warn without refusing the save. */
    sealed class SaveResult {
        data class Saved(
            val shortCode: String,
            val expiresAt: Long?,
            val alreadyExpired: Boolean,
        ) : SaveResult()

        data class Invalid(val reason: String) : SaveResult()
    }

    fun getShortCode(): String? = prefs.getString(KEY_SHORT_CODE, null)

    fun hasSavedPass(): Boolean = prefs.contains(KEY_SHORT_CODE)

    fun getExpiresAt(): Long? {
        val value = prefs.getLong(KEY_EXPIRES_AT, -1L)
        return if (value <= 0L) null else value
    }

    /** The doors this pass grants, discovered from the token and cached at save time. */
    fun getDoors(): List<Door> = Doors.fromJson(prefs.getString(KEY_DOORS, null))

    fun saveDoors(doors: List<Door>) {
        prefs.edit().putString(KEY_DOORS, Doors.toJson(doors)).apply()
    }

    fun getExpirySource(): String = prefs.getString(KEY_EXPIRY_SOURCE, SOURCE_UNKNOWN) ?: SOURCE_UNKNOWN

    fun getPassStatus(): PassStatus? {
        val code = getShortCode() ?: return null
        return PassStatus(code, getExpiresAt(), getExpirySource())
    }

    /**
     * Persists the short code parsed from [input], recording the save time and — when the [rawText]
     * (the untrimmed shared/pasted message) carries validity dates — the pass expiry.
     *
     * A failed parse never clears or overwrites an existing pass. When re-saving the *same* short
     * code without any date, a previously known expiry (including a manual one) is preserved; a new
     * short code resets the expiry to unknown.
     */
    fun savePassInput(input: String, rawText: String = input): SaveResult {
        val code = parseShortCode(input)
            ?: return SaveResult.Invalid("Invalid guest pass link or short code")

        val previousCode = getShortCode()
        val parsedExpiry = PassExpiry.parseExpiry(rawText)
        val now = System.currentTimeMillis()

        val editor = prefs.edit()
            .putString(KEY_SHORT_CODE, code)
            .putLong(KEY_SAVED_AT, now)
            .remove(KEY_LAST_NOTIFIED)

        val effectiveExpiry: Long? = when {
            parsedExpiry != null -> {
                editor.putLong(KEY_EXPIRES_AT, parsedExpiry).putString(KEY_EXPIRY_SOURCE, SOURCE_PARSED)
                parsedExpiry
            }
            code == previousCode && getExpiresAt() != null -> getExpiresAt()
            else -> {
                editor.remove(KEY_EXPIRES_AT).putString(KEY_EXPIRY_SOURCE, SOURCE_UNKNOWN)
                null
            }
        }

        // A different pass grants different doors — drop the stale cache so it is re-fetched.
        if (code != previousCode) editor.remove(KEY_DOORS)

        editor.apply()

        val alreadyExpired = effectiveExpiry != null && PassExpiry.isExpired(effectiveExpiry, now)
        return SaveResult.Saved(code, effectiveExpiry, alreadyExpired)
    }

    /** Lets the user set/correct the expiry when the share text carried no date. */
    fun setExpiryManually(epochMs: Long) {
        prefs.edit()
            .putLong(KEY_EXPIRES_AT, PassExpiry.endOfDay(epochMs))
            .putString(KEY_EXPIRY_SOURCE, SOURCE_MANUAL)
            .remove(KEY_LAST_NOTIFIED)
            .apply()
    }

    fun clearSavedPass() {
        prefs.edit()
            .remove(KEY_SHORT_CODE)
            .remove(KEY_SAVED_AT)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_EXPIRY_SOURCE)
            .remove(KEY_LAST_NOTIFIED)
            .remove(KEY_DOORS)
            .apply()
    }

    /** The reconstructed Alta guest-pass link for export/backup, or null if no pass is saved. */
    fun exportableGuestPassLink(): String? = getShortCode()?.let { guestPassLink(it) }

    // Reminder-worker dedup: the smallest threshold already notified (MAX = none yet).
    fun getLastNotifiedThreshold(): Int = prefs.getInt(KEY_LAST_NOTIFIED, Int.MAX_VALUE)

    fun setLastNotifiedThreshold(threshold: Int) {
        prefs.edit().putInt(KEY_LAST_NOTIFIED, threshold).apply()
    }

    companion object {
        private const val PREFS_NAME = "garage_unlock_config"
        private const val KEY_SHORT_CODE = "short_code"
        private const val KEY_SAVED_AT = "saved_at"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_EXPIRY_SOURCE = "expiry_source"
        private const val KEY_LAST_NOTIFIED = "last_notified_threshold"
        private const val KEY_DOORS = "doors_json"

        const val SOURCE_PARSED = "parsed"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_UNKNOWN = "unknown"

        private const val GUEST_PASS_BASE = "https://access.alta.avigilon.com/cloudKeyUnlock?shortCode="

        private val GUEST_PASS_URL = Regex(
            """https://access\.alta\.avigilon\.com/cloudKeyUnlock\?shortCode=[A-Za-z0-9]+""",
            RegexOption.IGNORE_CASE,
        )

        fun guestPassLink(shortCode: String): String = GUEST_PASS_BASE + shortCode

        fun extractGuestPassLink(input: String): String? =
            GUEST_PASS_URL.find(input)?.value

        fun normalizePassInput(input: String): String =
            extractGuestPassLink(input) ?: input.trim()

        fun parseShortCode(input: String): String? {
            val normalized = normalizePassInput(input)
            if (normalized.isEmpty()) return null

            val fromQuery = Uri.parse(normalized).getQueryParameter("shortCode")
            if (!fromQuery.isNullOrBlank()) return fromQuery.trim()

            val shortCodeParam = Regex("""shortCode=([A-Za-z0-9]+)""")
                .find(normalized)
                ?.groupValues
                ?.get(1)
            if (!shortCodeParam.isNullOrBlank()) return shortCodeParam

            if (normalized.matches(Regex("""[A-Za-z0-9]{8,32}"""))) return normalized

            return null
        }
    }
}
