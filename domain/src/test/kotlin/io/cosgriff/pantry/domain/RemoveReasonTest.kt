package io.cosgriff.pantry.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoveReasonTest {
    @Test
    fun `only using it up puts it on the shopping list`() {
        assertEquals(listOf(RemoveReason.UsedUp), RemoveReason.entries.filter { it.addsToShoppingList })
    }

    @Test
    fun `the dialog asks in the board's order, used it up first`() {
        assertEquals(listOf(RemoveReason.UsedUp, RemoveReason.ThrewOut, RemoveReason.AddedByMistake), RemoveReason.entries)
    }
}
