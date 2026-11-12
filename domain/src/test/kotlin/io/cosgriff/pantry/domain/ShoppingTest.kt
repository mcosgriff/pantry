package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun item(id: Long, name: String, amount: String, lowAt: String?) = PantryItem(
        id = id,
        name = name,
        location = Location.Dry,
        quantity = Measure(BigDecimal(amount), MeasureUnit.Each),
        purchased = today,
        lowStockAt = lowAt?.let(::BigDecimal),
    )

    private val items = listOf(item(1, "Test eggs", "6", "6"), item(2, "Test rice", "9", "2"), item(3, "Test coffee", "0", "1"))
    private val entries = listOf(
        ShoppingEntry(10, "Test scallions", ShoppingReason.ByHand, today),
        ShoppingEntry(11, "Test yogurt", ShoppingReason.UsedUp, today),
        ShoppingEntry(12, "test EGGS", ShoppingReason.ByHand, today),
    )

    @Test
    fun `running low first, then used up, then by hand, by name, without repeats`() {
        assertEquals(
            listOf("Test coffee", "Test eggs", "Test yogurt", "Test scallions"),
            shoppingList(items, entries).map { it.name },
        )
        assertEquals(listOf("low:3", "low:1", "entry:11", "entry:10"), shoppingList(items, entries).map { it.key })
    }

    @Test
    fun `a restocked item leaves the list`() {
        val restocked = items.map { if (it.id == 1L) it.copy(quantity = it.quantity.withNumber(BigDecimal("24"))) else it }
        assertEquals(listOf("Test coffee"), shoppingList(restocked, emptyList()).map { it.name })
    }

    @Test
    fun `trip progress counts only keys still on the list`() {
        val lines = shoppingList(items, entries)
        assertEquals(TripProgress(inCart = 2, total = 4), tripProgress(lines, setOf("low:1", "entry:10", "entry:99")))
        assertEquals(2, tripProgress(lines, setOf("low:1", "entry:10")).left)
    }

    @Test
    fun `what a planned meal needs comes after running low and before used up`() {
        val forMeal = ShoppingEntry(13, "Test cottage cheese", ShoppingReason.ForMeal, today, meal = "Test egg bites", mealDay = today.plusDays(1))
        assertEquals(
            listOf("Test coffee", "Test eggs", "Test cottage cheese", "Test yogurt", "Test scallions"),
            shoppingList(items, entries + forMeal).map { it.name },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a for-meal entry names its meal`() {
        ShoppingEntry(1, "Test", ShoppingReason.ForMeal, today)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a stored entry can't claim to be running low`() {
        ShoppingEntry(1, "Test", ShoppingReason.RunningLow, today)
    }
}
