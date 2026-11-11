package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PantryListTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun item(name: String, location: Location = Location.Fridge, expires: LocalDate? = null) = PantryItem(
        name = name,
        location = location,
        quantity = Quantity(BigDecimal.ONE, QuantityUnit.Each),
        purchased = today,
        expires = expires,
    )

    private val items = listOf(
        item("Baby spinach", expires = today.minusDays(1)),
        item("Large eggs", expires = today.plusDays(2)),
        item("Chicken thighs", Location.Freezer, today.plusDays(7)),
        item("Blueberries", Location.Freezer, today.plusDays(8)),
        item("Jasmine rice", Location.Dry),
    )

    @Test
    fun `no query and no location keeps every item in soonest-expiry order`() {
        assertEquals(
            listOf("Baby spinach", "Large eggs", "Chicken thighs", "Blueberries", "Jasmine rice"),
            pantryList(items.shuffled(), query = "", location = null).map { it.name },
        )
    }

    @Test
    fun `query matches part of a name, ignoring case and surrounding spaces`() {
        assertEquals(listOf("Chicken thighs"), pantryList(items, query = "  CHICK ", location = null).map { it.name })
    }

    @Test
    fun `location keeps only its items`() {
        assertEquals(
            listOf("Chicken thighs", "Blueberries"),
            pantryList(items, query = "", location = Location.Freezer).map { it.name },
        )
    }

    @Test
    fun `query and location together`() {
        assertEquals(emptyList<String>(), pantryList(items, query = "rice", location = Location.Fridge).map { it.name })
    }

    @Test
    fun `counts by location`() {
        assertEquals(
            mapOf(Location.Fridge to 2, Location.Freezer to 2, Location.Dry to 1),
            countByLocation(items),
        )
    }

    @Test
    fun `this week counts expired items and those within seven days`() {
        assertEquals(ExpirySummary(thisWeek = 3, expired = 1), expirySummary(items, today))
    }

    @Test
    fun `nothing dated soon`() {
        assertEquals(ExpirySummary(thisWeek = 0, expired = 0), expirySummary(listOf(item("Rice")), today))
    }
}
