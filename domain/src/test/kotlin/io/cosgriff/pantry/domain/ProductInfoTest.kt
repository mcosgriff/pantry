package io.cosgriff.pantry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductInfoTest {
    @Test
    fun `the name comes from the product, then its first brand`() {
        assertEquals("Test large eggs", ProductInfo(" Test large eggs ", "Test farm", "24 ct").suggestedName)
        assertEquals("Test farm", ProductInfo("", "Test farm, Test parent co", null).suggestedName)
        assertNull(ProductInfo(null, " ", null).suggestedName)
    }

    @Test
    fun `details show brand and size beside a name, and only size when the brand is the name`() {
        assertEquals(listOf("Test farm", "24 ct"), ProductInfo("Test large eggs", "Test farm", "24 ct").details)
        assertEquals(listOf("24 ct"), ProductInfo(null, "Test farm", "24 ct").details)
        assertEquals(emptyList<String>(), ProductInfo("Test", null, "").details)
    }

    @Test
    fun `the limiter lets 15 a minute through and frees each one a minute after it went`() {
        val limiter = LookupLimiter()
        repeat(15) { assertTrue(limiter.allow(1_000L + it)) }
        assertFalse(limiter.allow(30_000))
        // The first went out at 1 000, so one more may go a minute later; the rest are still counted.
        assertTrue(limiter.allow(61_000))
        assertFalse(limiter.allow(61_000))
    }
}
