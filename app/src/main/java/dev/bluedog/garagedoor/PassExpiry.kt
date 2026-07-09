// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parses and reasons about the guest pass's real expiry date.
 *
 * The re-resolved JWT is a short-lived unlock token (its `exp` is minutes away), so it is not a
 * reliable source for the ~6-month guest-pass validity. Instead we scrape validity dates out of the
 * raw Alta share text the user pastes/shares (which typically reads like
 * "Valid Jul 1 – Dec 30, 2026"), falling back to manual entry when no date is present.
 *
 * Pure java.util so it runs as a host-side JVM unit test and needs no core-library desugaring on
 * minSdk 23.
 */
object PassExpiry {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Thresholds (in days) at which the reminder worker notifies. Widest first. */
    val REMINDER_THRESHOLDS = listOf(7, 1)

    // Candidate date substrings: ISO (2026-12-30), US slash (12/30/2026), month name (Dec 30, 2026 /
    // December 30, 2026, comma optional). A range like "Jul 1 – Dec 30, 2026" yields the end date
    // "Dec 30, 2026" because only the end carries a year.
    private val CANDIDATE_REGEX = Regex(
        """\d{4}-\d{1,2}-\d{1,2}""" +
            """|\d{1,2}/\d{1,2}/\d{4}""" +
            """|[A-Za-z]{3,9}\.?\s+\d{1,2},?\s*\d{4}""",
    )

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd",
        "M/d/yyyy",
        "MMM d, yyyy",
        "MMM d yyyy",
        "MMMM d, yyyy",
        "MMMM d yyyy",
    )

    /**
     * Returns the end-of-day epoch millis of the latest date found in [rawText], or null if no date
     * is present. When a range is given, the later (end) date wins.
     */
    fun parseExpiry(rawText: String): Long? {
        var best: Long? = null
        for (match in CANDIDATE_REGEX.findAll(rawText)) {
            val epoch = tryParse(match.value) ?: continue
            if (best == null || epoch > best) best = epoch
        }
        return best
    }

    private fun tryParse(candidate: String): Long? {
        val cleaned = candidate.trim().replace(".", "")
        for (pattern in DATE_PATTERNS) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            try {
                val date = formatter.parse(cleaned) ?: continue
                return endOfDay(date.time)
            } catch (_: ParseException) {
                // Try the next pattern.
            }
        }
        return null
    }

    /** Snaps [epochMs] to 23:59:59.999 in the device's local time zone. */
    fun endOfDay(epochMs: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }

    /** Whole days from [nowMs] until [expiresAt]. Negative once the pass has expired. */
    fun daysRemaining(expiresAt: Long, nowMs: Long): Long =
        Math.floorDiv(expiresAt - nowMs, DAY_MS)

    fun isExpired(expiresAt: Long, nowMs: Long): Boolean = expiresAt < nowMs

    /**
     * The tightest reminder threshold (in [REMINDER_THRESHOLDS]) that currently applies, or null if
     * the pass is not yet within any threshold. Used by the worker to notify once per threshold
     * crossed — e.g. 6 days out → 7, within 1 day → 1.
     */
    fun activeReminderThreshold(expiresAt: Long, nowMs: Long): Int? {
        if (isExpired(expiresAt, nowMs)) return null
        val days = daysRemaining(expiresAt, nowMs)
        return REMINDER_THRESHOLDS.sorted().firstOrNull { days <= it }
    }
}
