package io.cosgriff.pantry.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaulTest {
    private val today = LocalDate.of(2026, 11, 10)
    private fun draft(name: String, location: Location) = ItemDraft.new(today, location).copy(name = name)

    private val haul = listOf(
        draft("Eggs", Location.Fridge),
        draft("Peas", Location.Freezer),
        draft("", Location.Fridge),
        draft("Rice", Location.Dry),
    )

    @Test
    fun `groups follow the location order, entries keep scan order and index`() {
        val groups = haulGroups(haul, listOf(Location.Fridge, Location.Freezer, Location.Dry))
        assertEquals(listOf(Location.Fridge, Location.Freezer, Location.Dry), groups.map { it.first })
        assertEquals(listOf(0, 2), groups[0].second.map { it.index })
    }

    @Test
    fun `empty locations are left out, unknown ones go last`() {
        val garage = Location.Custom("Garage", null, id = 4)
        val groups = haulGroups(listOf(draft("Soda", garage), draft("Rice", Location.Dry)), Location.defaults)
        assertEquals(listOf(Location.Dry, garage), groups.map { it.first })
    }

    @Test
    fun `a haul with an unnamed item can't be added yet`() {
        assertEquals(1, needingName(haul))
        assertNull(haulItems(haul))
        assertEquals(listOf("Eggs", "Peas", "Rice"), haulItems(haul.filter { it.name.isNotBlank() })?.map { it.name })
    }
}
