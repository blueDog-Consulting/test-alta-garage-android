package dev.bluedog.garagedoor

import android.content.Context
import android.net.Uri

class UnlockConfigStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getShortCode(): String? = prefs.getString(KEY_SHORT_CODE, null)

    fun hasSavedPass(): Boolean = prefs.contains(KEY_SHORT_CODE)

    fun savePassInput(input: String): Result<String> {
        val code = parseShortCode(input)
            ?: return Result.failure(IllegalArgumentException("Invalid guest pass link or short code"))
        prefs.edit().putString(KEY_SHORT_CODE, code).apply()
        return Result.success(code)
    }

    fun clearSavedPass() {
        prefs.edit().remove(KEY_SHORT_CODE).apply()
    }

    companion object {
        private const val PREFS_NAME = "garage_unlock_config"
        private const val KEY_SHORT_CODE = "short_code"

        private val GUEST_PASS_URL = Regex(
            """https://access\.alta\.avigilon\.com/cloudKeyUnlock\?shortCode=[A-Za-z0-9]+""",
            RegexOption.IGNORE_CASE,
        )

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
