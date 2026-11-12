package io.cosgriff.pantry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up products. */
class ContributionTest {
    @Test
    fun `the least sent is the barcode and the name`() {
        assertEquals(
            mapOf("code" to "041220576913", "product_name" to "Test sparkling water"),
            NewProduct("041220576913", "  Test sparkling water ").fields(),
        )
    }

    @Test
    fun `brand and size go only when typed, trimmed`() {
        assertEquals(
            mapOf("code" to "041220576913", "product_name" to "Test water", "add_brands" to "Test brand", "quantity" to "12 fl oz"),
            NewProduct("041220576913", "Test water", brand = " Test   brand ", size = "12 fl oz").fields(),
        )
        assertEquals(setOf("code", "product_name"), NewProduct("041220576913", "Test water", brand = " ", size = "").fields()?.keys)
    }

    @Test
    fun `no name, a store-packed label or a bad code is never sent`() {
        assertNull(NewProduct("041220576913", "  ").fields())
        assertNull(NewProduct("204711018426", "Test steak").fields())
        assertNull(NewProduct("2047110184265", "Test steak").fields())
        assertNull(NewProduct("041220576914", "Test water").fields())
        assertNull(NewProduct("12/../x", "Test water").fields())
        assertFalse(NewProduct("204711018426", "Test steak").shareable)
        assertTrue(NewProduct("041220576913", "Test water").shareable)
    }

    @Test
    fun `an EAN-13 with a leading zero goes as its UPC-A`() {
        assertEquals("041220576913", NewProduct("0041220576913", "Test water").fields()?.get("code"))
    }
}
