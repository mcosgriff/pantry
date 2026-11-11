package io.cosgriff.pantry.data

import io.cosgriff.pantry.domain.LookupLimiter
import io.cosgriff.pantry.domain.LookupResult
import io.cosgriff.pantry.domain.ProductInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Looks up a product by barcode (#9). */
fun interface ProductLookup {
    suspend fun lookUp(barcode: String): LookupResult
}

/** One HTTP GET: the status code and body, or `null` if the request didn't complete. */
fun interface HttpGet {
    fun get(url: String, userAgent: String): Pair<Int, String>?
}

/**
 * Open Food Facts' product API, as decided in `docs/adr/0001`: only the barcode goes out, with the `User-Agent`
 * Open Food Facts asks apps for, at most 15 lookups a minute. Name, brand and size come back; nothing else is
 * read. Any failure is [LookupResult.Failed], never an exception: the user names the product by hand instead.
 */
class OpenFoodFacts(
    private val http: HttpGet,
    private val userAgent: String,
    private val limiter: LookupLimiter = LookupLimiter(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val baseUrl: String = "https://world.openfoodfacts.org",
) : ProductLookup {
    override suspend fun lookUp(barcode: String): LookupResult {
        // Only a retail code of digits is ever sent; the scanner hands over nothing else.
        if (barcode.isEmpty() || !barcode.all(Char::isDigit)) return LookupResult.NotFound
        if (!limiter.allow(nowMillis())) return LookupResult.Failed
        val response = withContext(Dispatchers.IO) { http.get("$baseUrl/api/v2/product/$barcode.json?fields=$FIELDS", userAgent) }
            ?: return LookupResult.Failed
        return parse(response.first, response.second)
    }

    companion object {
        private const val FIELDS = "product_name,brands,quantity"

        /** Reads Open Food Facts' answer: `status` 1 with a `product`, or 0 (HTTP 404) for an unknown barcode. */
        fun parse(code: Int, body: String): LookupResult {
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return LookupResult.NotFound
            if (code != HttpURLConnection.HTTP_OK) return LookupResult.Failed
            return try {
                val json = JSONObject(body)
                val product = json.optJSONObject("product")
                if (json.optInt("status") != 1 || product == null) return LookupResult.NotFound
                val info = ProductInfo(product.text("product_name"), product.text("brands"), product.text("quantity"))
                if (info.suggestedName == null) LookupResult.NotFound else LookupResult.Found(info)
            } catch (_: JSONException) {
                LookupResult.Failed
            }
        }

        private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
    }
}

/** [HttpGet] over the platform's `HttpURLConnection`: no HTTP library, so nothing new to check for trackers. */
class UrlConnectionGet(private val timeoutMillis: Int = 5_000) : HttpGet {
    override fun get(url: String, userAgent: String): Pair<Int, String>? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        null
    }
}
