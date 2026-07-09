// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassExpiryTest {

    private fun endOfLocalDay(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance(Locale.US).apply {
            clear()
            set(year, month - 1, day, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }

    @Test
    fun parsesIsoDate() {
        assertEquals(endOfLocalDay(2026, 12, 30), PassExpiry.parseExpiry("expires 2026-12-30"))
    }

    @Test
    fun parsesUsSlashDate() {
        assertEquals(endOfLocalDay(2026, 12, 30), PassExpiry.parseExpiry("valid until 12/30/2026"))
    }

    @Test
    fun parsesAbbreviatedMonthName() {
        assertEquals(endOfLocalDay(2026, 12, 30), PassExpiry.parseExpiry("Valid until Dec 30, 2026"))
    }

    @Test
    fun parsesFullMonthName() {
        assertEquals(
            endOfLocalDay(2026, 12, 30),
            PassExpiry.parseExpiry("expires on December 30, 2026"),
        )
    }

    @Test
    fun rangeTakesEndDate() {
        // "Jul 1" carries no year, so only the end date "Dec 30, 2026" is parseable and wins.
        assertEquals(
            endOfLocalDay(2026, 12, 30),
            PassExpiry.parseExpiry("Guest pass valid Jul 1 – Dec 30, 2026"),
        )
    }

    @Test
    fun picksLatestOfMultipleDates() {
        assertEquals(
            endOfLocalDay(2026, 12, 30),
            PassExpiry.parseExpiry("Issued 2026-07-01, expires 2026-12-30"),
        )
    }

    @Test
    fun returnsNullWhenNoDatePresent() {
        assertNull(PassExpiry.parseExpiry("https://access.alta.avigilon.com/cloudKeyUnlock?shortCode=ABC123"))
        assertNull(PassExpiry.parseExpiry("ABC123XYZ"))
        assertNull(PassExpiry.parseExpiry(""))
    }

    @Test
    fun rejectsNonMonthWordsBeforeDates() {
        // "until 30, 2026" is not a real date; nothing valid → null.
        assertNull(PassExpiry.parseExpiry("expires until 30, 2026 sometime"))
    }

    @Test
    fun readsExpiryFromJwtExpClaim() {
        // exp is Unix seconds; result is millis.
        assertEquals(1795284540L * 1000L, PassExpiry.fromJwtExp("""{"exp":1795284540,"orgId":1}"""))
    }

    @Test
    fun jwtExpMissingOrZeroReturnsNull() {
        assertNull(PassExpiry.fromJwtExp("""{"orgId":1}"""))
        assertNull(PassExpiry.fromJwtExp("""{"exp":0}"""))
    }

    @Test
    fun daysRemainingIsPositiveBeforeExpiry() {
        val now = endOfLocalDay(2026, 1, 1)
        val expires = PassExpiry.endOfDay(now) + 45 * PassExpiry.DAY_MS
        assertEquals(45, PassExpiry.daysRemaining(expires, now))
        assertFalse(PassExpiry.isExpired(expires, now))
    }

    @Test
    fun isExpiredAfterExpiry() {
        val expires = endOfLocalDay(2026, 1, 1)
        val now = expires + PassExpiry.DAY_MS
        assertTrue(PassExpiry.isExpired(expires, now))
        assertTrue(PassExpiry.daysRemaining(expires, now) < 0)
    }

    @Test
    fun activeReminderThresholdCrossesSevenThenTwoThenExpired() {
        val expires = endOfLocalDay(2026, 6, 30)
        // 10 days out → no threshold yet.
        assertNull(PassExpiry.activeReminderThreshold(expires, expires - 10 * PassExpiry.DAY_MS))
        // 5 days out → 7-day threshold.
        assertEquals(
            7,
            PassExpiry.activeReminderThreshold(expires, expires - 5 * PassExpiry.DAY_MS),
        )
        // 2 days out → 2-day threshold.
        assertEquals(
            2,
            PassExpiry.activeReminderThreshold(expires, expires - 2 * PassExpiry.DAY_MS),
        )
        // Within a day (not yet expired) → still the 2-day threshold.
        assertEquals(
            2,
            PassExpiry.activeReminderThreshold(expires, expires - 12 * 60 * 60 * 1000),
        )
        // Expired → the expired sentinel (0), so an expired notification can fire.
        assertEquals(
            PassExpiry.EXPIRED_THRESHOLD,
            PassExpiry.activeReminderThreshold(expires, expires + PassExpiry.DAY_MS),
        )
    }
}
