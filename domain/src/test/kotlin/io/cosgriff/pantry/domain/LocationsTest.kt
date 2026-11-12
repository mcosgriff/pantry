package io.cosgriff.pantry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationsTest {
    private val garage = Location.Custom("Garage", null, id = 7)
    private val all = Location.defaults + garage

    @Test
    fun `keys are the built-in names and custom ids, and read back`() {
        assertEquals(listOf("DRY", "FRIDGE", "FREEZER", "custom:7"), all.map { LocationKey.of(it).text })
        all.forEach { assertEquals(LocationKey.of(it), LocationKey.parse(LocationKey.of(it).text)) }
    }

    @Test
    fun `text the app doesn't understand has no key`() {
        listOf("", "dry", "PANTRY", "custom:", "custom:x", "custom:0", "7").forEach { assertNull(it, LocationKey.parse(it)) }
    }

    @Test
    fun `an unsaved location has no key`() {
        assertNull(Location.Custom("New", null).key)
    }

    @Test
    fun `a key finds the location as it is now`() {
        val renamed = garage.copy(name = "Garage fridge")
        assertEquals(renamed, (Location.defaults + renamed).withKey(garage.key))
        assertNull(Location.defaults.withKey(garage.key))
        assertNull(all.withKey(null))
    }

    // The deleted-location policy, one use at a time. `null` stands for a location that was deleted.

    @Test
    fun `settings - a deleted default location reads as last used`() {
        assertEquals(DefaultLocation.Fixed(garage), DeletedLocation.defaultLocation(garage))
        assertEquals(DefaultLocation.LastUsed, DeletedLocation.defaultLocation(null))
    }

    @Test
    fun `a new item - default, else last used, else Dry`() {
        assertEquals(garage, DeletedLocation.newItem(default = garage, lastUsed = Location.Freezer))
        assertEquals(Location.Freezer, DeletedLocation.newItem(default = null, lastUsed = Location.Freezer))
        assertEquals(Location.Dry, DeletedLocation.newItem(default = null, lastUsed = null))
    }

    @Test
    fun `a scanned product - its known product's location, else where a new item starts`() {
        assertEquals(garage, DeletedLocation.scannedProduct(known = garage, newItem = Location.Fridge))
        assertEquals(Location.Fridge, DeletedLocation.scannedProduct(known = null, newItem = Location.Fridge))
    }

    @Test
    fun `the pantry list - a deleted tab falls back to All, a renamed one stays chosen`() {
        assertEquals(Location.Fridge, DeletedLocation.chosenTab(Location.Fridge, all))
        assertNull(DeletedLocation.chosenTab(garage, Location.defaults))
        assertNull(DeletedLocation.chosenTab(null, all))
        val renamed = garage.copy(name = "Garage fridge")
        assertEquals(renamed, DeletedLocation.chosenTab(garage, Location.defaults + renamed))
    }
}
