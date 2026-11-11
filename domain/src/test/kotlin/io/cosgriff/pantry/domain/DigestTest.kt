package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun item(name: String, days: Long?) = PantryItem(
        name = name,
        location = Location.Fridge,
        quantity = Measure(BigDecimal.ONE, MeasureUnit.Each),
        purchased = today,
        expires = days?.let(today::plusDays),
    )

    private val items = listOf(
        item("Spinach", -1),
        item("Yogurt", -6),
        item("Milk", 0),
        item("Eggs", 2),
        item("Butter", 3),
        item("Hummus", 4),
        item("Rice", null),
    )

    private val both = PantrySettings(notifyExpiringSoon = true, notifyExpired = true)

    @Test
    fun `counts items expiring within the warning window, today and its last day included, and those expired`() {
        assertEquals(ExpiryDigest(expiringSoon = 3, expired = 2), expiryDigest(items, today, both))
    }

    @Test
    fun `the warning window follows Warn me`() {
        assertEquals(ExpiryDigest(expiringSoon = 1, expired = 2), expiryDigest(items, today, both.copy(warningDays = 0)))
        assertEquals(ExpiryDigest(expiringSoon = 4, expired = 2), expiryDigest(items, today, both.copy(warningDays = 7)))
    }

    @Test
    fun `an alert that is off counts nothing`() {
        assertEquals(ExpiryDigest(expiringSoon = 3, expired = 0), expiryDigest(items, today, both.copy(notifyExpired = false)))
        assertEquals(ExpiryDigest(expiringSoon = 0, expired = 2), expiryDigest(items, today, both.copy(notifyExpiringSoon = false)))
        assertTrue(expiryDigest(items, today, PantrySettings()).isEmpty)
    }

    @Test
    fun `nothing expiring is an empty digest`() {
        assertTrue(expiryDigest(listOf(item("Rice", null), item("Beans", 30)), today, both).isEmpty)
        assertFalse(ExpiryDigest(expiringSoon = 0, expired = 1).isEmpty)
    }

    @Test
    fun `the digest is on while either alert is`() {
        assertFalse(PantrySettings().digestOn)
        assertTrue(PantrySettings(notifyExpiringSoon = true).digestOn)
        assertTrue(PantrySettings(notifyExpired = true).digestOn)
    }

    private val zone = ZoneId.of("America/Denver")
    private val eight = LocalTime.of(8, 0)

    @Test
    fun `next digest is today while its time is ahead, otherwise tomorrow`() {
        val early = ZonedDateTime.of(today, LocalTime.of(6, 30), zone)
        assertEquals(ZonedDateTime.of(today, eight, zone), nextDigest(early, eight))
        val onTime = ZonedDateTime.of(today, eight, zone)
        assertEquals(ZonedDateTime.of(today.plusDays(1), eight, zone), nextDigest(onTime, eight))
        val late = ZonedDateTime.of(today, LocalTime.of(21, 0), zone)
        assertEquals(ZonedDateTime.of(today.plusDays(1), eight, zone), nextDigest(late, eight))
    }

    @Test
    fun `next digest keeps the wall time across a daylight saving change`() {
        // Clocks go back at 2:00 on 2026-11-01 in Denver: that day is 25 hours long.
        val fall = nextDigest(ZonedDateTime.of(LocalDate.of(2026, 10, 31), LocalTime.of(9, 0), zone), eight)
        assertEquals(ZonedDateTime.of(LocalDate.of(2026, 11, 1), eight, zone), fall)
        assertEquals(eight, fall.toLocalTime())
        // Clocks go forward at 2:00 on 2026-03-08: 2:30 doesn't exist that day, so it goes out at 3:30.
        val spring = nextDigest(ZonedDateTime.of(LocalDate.of(2026, 3, 7), LocalTime.of(9, 0), zone), LocalTime.of(2, 30))
        assertEquals(LocalTime.of(3, 30), spring.toLocalTime())
    }
}
