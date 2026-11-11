package io.cosgriff.pantry.domain

/**
 * What happens when a scanned product isn't in Open Food Facts and the user names it (#42, `docs/adr/0004`):
 * offer to share it ([Ask], at install), go straight to the form ([Always]), or never offer ([Never]).
 * Nothing is sent until the user is signed in and taps Share.
 */
enum class ContributeChoice { Ask, Always, Never }

/**
 * A product to add to Open Food Facts (#42): the barcode, the name the user typed, and the brand and size if
 * they typed them, as printed on the package.
 */
data class NewProduct(val barcode: String, val name: String, val brand: String = "", val size: String = "") {
    /**
     * The fields sent to Open Food Facts' write API, the least it takes for a new product: `code` and
     * `product_name`, plus `add_brands` and `quantity` only when the user typed them. Nothing about the pantry,
     * the item, the user or the phone. `null` when it can't be sent: no name, or a code that isn't a product's
     * (a store-packed label changes with every package and is never sent, `docs/adr/0001`).
     */
    fun fields(): Map<String, String>? {
        val code = (readRetailCode(barcode) as? ScannedCode.Product)?.code ?: return null
        val productName = name.clean() ?: return null
        return buildMap {
            put("code", code)
            put("product_name", productName)
            // add_brands, not brands: Open Food Facts asks apps to add to a list rather than replace it.
            brand.clean()?.let { put("add_brands", it) }
            this@NewProduct.size.clean()?.let { put("quantity", it) } // not the map's own size
        }
    }

    /** Whether there is enough to share: a product's barcode and a name. */
    val shareable: Boolean get() = fields() != null
}

private val WHITESPACE = Regex("""\s+""")

/** Trimmed, runs of spaces made one; `null` if nothing is left. */
private fun String.clean(): String? = trim().replace(WHITESPACE, " ").takeIf { it.isNotEmpty() }
