package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemDraftTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `a new draft is one each in Dry, bought today, with no date`() {
        val draft = ItemDraft.new(today)
        assertEquals(Location.Dry, draft.location)
        assertEquals(Quantity(BigDecimal.ONE, QuantityUnit.Each), draft.quantity)
        assertEquals(today, draft.purchased)
        assertNull(draft.expires)
    }

    @Test
    fun `a new draft can start in another location with its suggested date`() {
        assertEquals(today.plusDays(7), ItemDraft.new(today, Location.Fridge).expires)
    }

    @Test
    fun `moving a new item follows the location's shelf life until a date is set by hand`() {
        val draft = ItemDraft.new(today).withLocation(Location.Freezer)
        assertEquals(today.plusMonths(3), draft.expires)
        val fixed = draft.withExpires(today.plusDays(10)).withLocation(Location.Fridge)
        assertEquals(today.plusDays(10), fixed.expires)
    }

    @Test
    fun `moving to a location with no shelf life clears a suggested date`() {
        assertNull(ItemDraft.new(today, Location.Fridge).withLocation(Location.Dry).expires)
    }

    @Test
    fun `a stored item keeps its date when moved`() {
        val stored = PantryItem(
            id = 5,
            name = "Milk",
            location = Location.Fridge,
            quantity = Quantity(BigDecimal.ONE, QuantityUnit.Each),
            purchased = today,
            expires = today.plusDays(2),
        )
        assertEquals(today.plusDays(2), ItemDraft.of(stored).withLocation(Location.Freezer).expires)
    }

    @Test
    fun `changing the purchase date moves a suggested date with it`() {
        val draft = ItemDraft.new(today, Location.Fridge).withPurchased(today.minusDays(2))
        assertEquals(today.plusDays(5), draft.expires)
    }

    @Test
    fun `quick picks count from today`() {
        assertEquals(today.plusDays(3), QuickPick.ThreeDays.from(today))
        assertEquals(today.plusWeeks(1), QuickPick.OneWeek.from(today))
        assertEquals(LocalDate.of(2026, 12, 10), QuickPick.OneMonth.from(today))
        assertNull(QuickPick.NoDate.from(today))
    }

    @Test
    fun `stepper adds and removes one, never below zero`() {
        val q = Quantity(BigDecimal("0.6"), QuantityUnit.Bag)
        assertEquals(BigDecimal("1.6"), q.stepped(1).amount)
        assertEquals(BigDecimal.ZERO, q.stepped(-1).amount.stripTrailingZeros())
        assertEquals(BigDecimal("17"), Quantity(BigDecimal("18"), QuantityUnit.Each).stepped(-1).amount)
    }

    @Test
    fun `blank name is not ready to save, a named one becomes an item`() {
        val draft = ItemDraft.new(today).copy(name = "  ")
        assertNull(draft.toItem())
        val item = draft.copy(name = " Eggs ", notes = " fresh ").toItem()
        assertEquals("Eggs", item?.name)
        assertEquals("fresh", item?.notes)
    }

    @Test
    fun `round trip keeps every field of a stored item`() {
        val stored = PantryItem(
            id = 9,
            name = "Ground beef",
            barcode = "123",
            location = Location.Freezer,
            quantity = Quantity(BigDecimal("1.24"), QuantityUnit.Pound),
            purchased = today,
            expires = today.plusMonths(3),
            opened = true,
            lowStockAt = BigDecimal("0.5"),
            notes = "Test",
        )
        assertEquals(stored, ItemDraft.of(stored).toItem())
    }
}
