package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class UseSoonTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun item(name: String, days: Long?, location: Location = Location.Fridge, opened: Boolean = false) = PantryItem(
        name = name,
        location = location,
        quantity = Quantity(BigDecimal.ONE, QuantityUnit.Each),
        purchased = today,
        expires = days?.let(today::plusDays),
        opened = opened,
    )

    @Test
    fun `groups by expired, this week and next week, soonest first`() {
        val groups = useSoon(
            listOf(
                item("Hummus", 11),
                item("Spinach", -1),
                item("Eggs", 2),
                item("Bread", 7),
                item("Mozzarella", 8),
                item("Rice", null),
                item("Beef", 15),
                item("Milk", 0),
            ),
            today,
        ).mapValues { (_, items) -> items.map { it.name } }
        assertEquals(
            mapOf(
                UseSoonGroup.Expired to listOf("Spinach"),
                UseSoonGroup.ThisWeek to listOf("Milk", "Eggs", "Bread"),
                UseSoonGroup.NextWeek to listOf("Mozzarella", "Hummus"),
            ),
            groups,
        )
        assertEquals(listOf(UseSoonGroup.Expired, UseSoonGroup.ThisWeek, UseSoonGroup.NextWeek), groups.keys.toList())
    }

    @Test
    fun `empty groups are left out`() {
        assertEquals(setOf(UseSoonGroup.NextWeek), useSoon(listOf(item("Hummus", 14)), today).keys)
    }

    @Test
    fun `quick action`() {
        assertEquals(QuickAction.ThrowOut, quickAction(item("Spinach", -1), today))
        assertEquals(QuickAction.ThrowOut, quickAction(item("Peas", -1, Location.Freezer), today))
        assertEquals(QuickAction.UsedUp, quickAction(item("Peas", 2, Location.Freezer), today))
        assertEquals(QuickAction.UsedUp, quickAction(item("Yogurt", 2, opened = true), today))
        assertEquals(QuickAction.Freeze, quickAction(item("Chicken", 3), today))
        assertEquals(QuickAction.Freeze, quickAction(item("Bread", 0, Location.Dry), today))
    }

    @Test
    fun `freezing moves the item and dates it from the freezer's shelf life`() {
        val frozen = freeze(item("Chicken", 3), today)
        assertEquals(Location.Freezer, frozen.location)
        assertEquals(LocalDate.of(2027, 2, 10), frozen.expires)
    }
}
