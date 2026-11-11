package io.cosgriff.pantry.domain

/**
 * What Open Food Facts knows about a barcode (#9): its name, brand and size as the database has them, any of
 * them possibly missing. Text only; no photos (`docs/adr/0001`).
 */
data class ProductInfo(val name: String?, val brand: String?, val size: String?) {
    /**
     * The name to start the item with: the product's name, or failing that its brand; `null` if Open Food Facts
     * has neither, and the user names it as for an unknown barcode.
     */
    val suggestedName: String?
        get() = name.clean() ?: brand.clean()?.substringBefore(',')?.trim()

    /** Brand and size for the line under the name ("Kirkland Signature · 24 ct"), the parts it has. */
    val details: List<String>
        get() = listOfNotNull(brand.clean()?.takeIf { name.clean() != null }, size.clean())

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

/** How a lookup went. Anything but [Found] leaves the user to name the product, as for an unknown barcode. */
sealed interface LookupResult {
    data class Found(val info: ProductInfo) : LookupResult

    /** Open Food Facts doesn't have it, or has nothing to name it by. */
    data object NotFound : LookupResult

    /** Offline, slow, an error, or over the rate limit. */
    data object Failed : LookupResult
}

/**
 * Open Food Facts allows 15 product reads a minute from one address; this keeps the app under it. [allow] says
 * whether a lookup may go out at [nowMillis], and counts it if so.
 */
class LookupLimiter(private val perMinute: Int = 15) {
    private val sent = ArrayDeque<Long>()

    @Synchronized
    fun allow(nowMillis: Long): Boolean {
        while (sent.isNotEmpty() && nowMillis - sent.first() >= MINUTE) sent.removeFirst()
        if (sent.size >= perMinute) return false
        sent.addLast(nowMillis)
        return true
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
