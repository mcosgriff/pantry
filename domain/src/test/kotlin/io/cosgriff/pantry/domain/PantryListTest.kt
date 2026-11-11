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
    fun `query matches notes and barcodes, ignoring spaces in a barcode`() {
        val tagged = items + item("Mystery tin", Location.Dry).copy(notes = "Chickpeas for hummus", barcode = "096619321084")
        assertEquals(listOf("Mystery tin"), pantryList(tagged, "hummus", null).map { it.name })
        assertEquals(listOf("Mystery tin"), pantryList(tagged, "96619 32108", null).map { it.name })
    }

    @Test
    fun `query searches every location, whatever tab is chosen`() {
        assertEquals(listOf("Jasmine rice"), pantryList(items, "rice", Location.Fridge).map { it.name })
    }

    @Test
    fun `location keeps only its items`() {
        assertEquals(
            listOf("Chicken thighs", "Blueberries"),
            pantryList(items, query = "", location = Location.Freezer).map { it.name },
        )
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

    private fun stocked(id: Long, name: String, amount: String, lowAt: String? = null, opened: Boolean = false, bought: LocalDate = today) =
        PantryItem(
            id = id,
            name = name,
            location = Location.Dry,
            quantity = Quantity(BigDecimal(amount), QuantityUnit.Each),
            purchased = bought,
            opened = opened,
            lowStockAt = lowAt?.let(::BigDecimal),
        )

    private val stock = listOf(
        stocked(1, "beans", "5", lowAt = "6"),
        stocked(2, "Apples", "2"),
        stocked(3, "Coffee", "1", lowAt = "1", opened = true, bought = today.minusDays(6)),
        stocked(4, "Dates", "9", opened = true, bought = today.minusDays(7)),
    )

    @Test
    fun `sort by name, recently added and lowest stock`() {
        assertEquals(listOf("Apples", "beans", "Coffee", "Dates"), pantryList(stock, "", null, SortOrder.NameAToZ).map { it.name })
        assertEquals(listOf("Dates", "Coffee", "Apples", "beans"), pantryList(stock, "", null, SortOrder.RecentlyAdded).map { it.name })
        assertEquals(listOf("Coffee", "beans", "Apples", "Dates"), pantryList(stock, "", null, SortOrder.LowestStockFirst).map { it.name })
    }

    @Test
    fun `filters keep items matching all of them`() {
        fun only(vararg f: ItemFilter) = pantryList(stock, "", null, SortOrder.NameAToZ, f.toSet(), today).map { it.name }
        assertEquals(listOf("Coffee", "Dates"), only(ItemFilter.Opened))
        assertEquals(listOf("beans", "Coffee"), only(ItemFilter.LowStock))
        assertEquals(listOf("Apples", "beans", "Coffee"), only(ItemFilter.AddedThisWeek))
        assertEquals(listOf("Coffee"), only(ItemFilter.Opened, ItemFilter.LowStock, ItemFilter.AddedThisWeek))
        assertEquals(listOf("Apples", "beans", "Coffee", "Dates"), only(ItemFilter.NoExpiryDate))
        assertEquals(emptyList<String>(), pantryList(items, "", null, filters = setOf(ItemFilter.NoExpiryDate)).filter { it.expires != null })
    }
}
