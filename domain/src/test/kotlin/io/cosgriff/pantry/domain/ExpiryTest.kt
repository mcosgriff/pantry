package io.cosgriff.pantry.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `no date`() {
        assertEquals(ExpiryStatus.NoDate, expiryStatus(expires = null, today = today))
    }

    @Test
    fun `expired yesterday`() {
        assertEquals(ExpiryStatus.Expired(daysAgo = 1), expiryStatus(today.minusDays(1), today))
    }

    @Test
    fun `expires today is inside the window, not expired`() {
        assertEquals(ExpiryStatus.ExpiresSoon(daysLeft = 0), expiryStatus(today, today))
    }

    @Test
    fun `window boundary is inside the window`() {
        assertEquals(ExpiryStatus.ExpiresSoon(daysLeft = 3), expiryStatus(today.plusDays(3), today))
    }

    @Test
    fun `one day past the window is fine`() {
        assertEquals(ExpiryStatus.Fine(daysLeft = 4), expiryStatus(today.plusDays(4), today))
    }

    @Test
    fun `custom warning window`() {
        assertEquals(
            ExpiryStatus.ExpiresSoon(daysLeft = 7),
            expiryStatus(today.plusDays(7), today, warningDays = 7),
        )
        assertEquals(ExpiryStatus.Fine(daysLeft = 1), expiryStatus(today.plusDays(1), today, warningDays = 0))
    }

    @Test
    fun `counts days across a month and year end`() {
        val dec30 = LocalDate.of(2026, 12, 30)
        assertEquals(ExpiryStatus.Fine(daysLeft = 33), expiryStatus(LocalDate.of(2027, 2, 1), dec30))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative warning window is rejected`() {
        expiryStatus(today, today, warningDays = -1)
    }

    @Test
    fun `suggested expiry is purchase date plus location shelf life`() {
        val purchased = LocalDate.of(2026, 11, 8)
        assertEquals(LocalDate.of(2026, 11, 15), suggestedExpiry(purchased, Location.Fridge))
        assertEquals(LocalDate.of(2027, 2, 8), suggestedExpiry(purchased, Location.Freezer))
        assertNull(suggestedExpiry(purchased, Location.Dry))
    }

    @Test
    fun `freezer shelf life clamps to month end`() {
        assertEquals(
            LocalDate.of(2027, 2, 28),
            suggestedExpiry(LocalDate.of(2026, 11, 30), Location.Freezer),
        )
    }

    @Test
    fun `custom location uses its own shelf life`() {
        val garage = Location.Custom(name = "Garage fridge", shelfLife = java.time.Period.ofDays(5))
        assertEquals(today.plusDays(5), suggestedExpiry(today, garage))
        assertNull(suggestedExpiry(today, Location.Custom(name = "Cellar", shelfLife = null)))
    }
}
