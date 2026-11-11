package io.cosgriff.pantry.data

import io.cosgriff.pantry.domain.LookupLimiter
import io.cosgriff.pantry.domain.LookupResult
import io.cosgriff.pantry.domain.ProductInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs on Robolectric for the platform's `org.json`. Made-up products; no request leaves the test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenFoodFactsTest {
    private val requests = mutableListOf<Pair<String, String>>()

    private fun off(code: Int = 200, body: String = FOUND, limiter: LookupLimiter = LookupLimiter()) =
        OpenFoodFacts({ url, agent -> requests += url to agent; code to body }, userAgent = "Pantry/1.0 (test)", limiter = limiter, nowMillis = { 0L })

    @Test
    fun `sends only the barcode, asks for three fields, and names the app`() = runBlocking {
        off().lookUp("096619321087")
        assertEquals(
            listOf("https://world.openfoodfacts.org/api/v2/product/096619321087.json?fields=product_name,brands,quantity" to "Pantry/1.0 (test)"),
            requests,
        )
    }

    @Test
    fun `reads name, brand and size`() = runBlocking {
        assertEquals(LookupResult.Found(ProductInfo("Test large eggs", "Test farm", "24 ct")), off().lookUp("096619321087"))
    }

    @Test
    fun `an unknown barcode, or one with nothing to name it by, is not found`() = runBlocking {
        assertEquals(LookupResult.NotFound, off(404, """{"status":0,"status_verbose":"product not found"}""").lookUp("096619321087"))
        assertEquals(LookupResult.NotFound, off(body = """{"status":1,"product":{"quantity":"1 l"}}""").lookUp("096619321087"))
    }

    @Test
    fun `errors, bad answers and no connection fail quietly`() = runBlocking {
        assertEquals(LookupResult.Failed, off(503, "busy").lookUp("096619321087"))
        assertEquals(LookupResult.Failed, off(body = "<html>").lookUp("096619321087"))
        assertEquals(LookupResult.Failed, OpenFoodFacts({ _, _ -> null }, "Pantry/1.0 (test)").lookUp("096619321087"))
    }

    @Test
    fun `nothing but digits is ever sent`() = runBlocking {
        assertEquals(LookupResult.NotFound, off().lookUp("12/../../x"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `past the rate limit no request goes out`() = runBlocking {
        val lookup = off(limiter = LookupLimiter(perMinute = 1))
        lookup.lookUp("096619321087")
        assertEquals(LookupResult.Failed, lookup.lookUp("096619321087"))
        assertEquals(1, requests.size)
    }

    private companion object {
        const val FOUND = """{"code":"096619321087","product":{"brands":"Test farm","product_name":"Test large eggs","quantity":"24 ct"},"status":1,"status_verbose":"product found"}"""
    }
}
