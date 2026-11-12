package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryItemTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun item(
        name: String,
        expires: LocalDate? = null,
        amount: String = "1",
        lowStockAt: String? = null,
    ) = PantryItem(
        name = name,
        location = Location.Dry,
        quantity = Measure(BigDecimal(amount), MeasureUnit.Each),
        expires = expires,
        purchased = today,
        lowStockAt = lowStockAt?.let(::BigDecimal),
    )

    @Test
    fun `sorts soonest expiry first and undated items last`() {
        val items = listOf(
            item("Rice"),
            item("Milk", expires = today.plusDays(5)),
            item("Yogurt", expires = today.minusDays(1)),
            item("Beans"),
            item("Eggs", expires = today.plusDays(2)),
        )
        assertEquals(
            listOf("Yogurt", "Eggs", "Milk", "Beans", "Rice"),
            items.sortedWith(PantryItem.BySoonestExpiry).map { it.name },
        )
    }

    @Test
    fun `same expiry sorts by name ignoring case`() {
        val items = listOf(item("apples", today), item("Bread", today), item("Apricots", today))
        assertEquals(
            listOf("apples", "Apricots", "Bread"),
            items.sortedWith(PantryItem.BySoonestExpiry).map { it.name },
        )
    }

    @Test
    fun `low stock at or under threshold`() {
        assertTrue(item("Eggs", amount = "6", lowStockAt = "6").isLowStock)
        assertTrue(item("Eggs", amount = "2", lowStockAt = "6").isLowStock)
        assertFalse(item("Eggs", amount = "7", lowStockAt = "6").isLowStock)
    }

    @Test
    fun `low stock compares by value, not scale`() {
        assertTrue(item("Flour", amount = "1.50", lowStockAt = "1.5").isLowStock)
    }

    @Test
    fun `no threshold is never low stock`() {
        assertFalse(item("Salt", amount = "0").isLowStock)
    }

    @Test
    fun `item expiry status uses its date`() {
        assertEquals(ExpiryStatus.ExpiresSoon(2), item("Eggs", today.plusDays(2)).expiryStatus(today))
        assertEquals(ExpiryStatus.NoDate, item("Rice").expiryStatus(today))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative quantity is rejected`() {
        Measure(BigDecimal("-1"), MeasureUnit.Pound)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank name is rejected`() {
        item(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative low-stock threshold is rejected`() {
        item("Eggs", lowStockAt = "-1")
    }

    @Test
    fun `default locations and their shelf lives`() {
        assertEquals(listOf(Location.Dry, Location.Fridge, Location.Freezer), Location.defaults)
        assertEquals(null, Location.Dry.shelfLife)
        assertEquals(java.time.Period.ofDays(7), Location.Fridge.shelfLife)
        assertEquals(java.time.Period.ofMonths(3), Location.Freezer.shelfLife)
    }
}
