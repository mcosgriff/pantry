package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One thing in the pantry.
 *
 * @property id the stored item's id; 0 for one not saved yet.
 * @property quantity how much is left: a measure or a packaged amount (docs/adr/0002).
 * @property lowStockAt warn when [quantity] is at or under this amount, counted in the quantity's unit
 *   ("at 6 each" on the Item details board). `null`: no alert.
 * @property density the item's own density, which wins over the built-in table (#56). `null`: use the table.
 */
data class PantryItem(
    val name: String,
    val location: Location,
    val quantity: Amount,
    val purchased: LocalDate,
    val expires: LocalDate? = null,
    val barcode: String? = null,
    val opened: Boolean = false,
    val lowStockAt: BigDecimal? = null,
    val notes: String = "",
    val density: Density? = null,
    val id: Long = 0,
) {
    init {
        require(name.isNotBlank()) { "Item name must not be blank" }
        require(lowStockAt == null || lowStockAt.signum() >= 0) { "Low-stock threshold must not be negative" }
    }

    val isLowStock: Boolean
        get() = lowStockAt != null && quantity.number <= lowStockAt

    fun expiryStatus(today: LocalDate, warningDays: Int = DEFAULT_WARNING_DAYS): ExpiryStatus =
        expiryStatus(expires, today, warningDays)

    companion object {
        /** Soonest expiry first, undated items last; ties by name, ignoring case. */
        val BySoonestExpiry: Comparator<PantryItem> =
            compareBy<PantryItem, LocalDate?>(nullsLast()) { it.expires }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
}
