package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One thing in the pantry.
 *
 * @property lowStockAt warn when [quantity] is at or under this amount, counted in the quantity's unit
 *   ("at 6 each" on the Item details board). `null`: no alert.
 */
data class PantryItem(
    val name: String,
    val location: Location,
    val quantity: Quantity,
    val purchased: LocalDate,
    val expires: LocalDate? = null,
    val barcode: String? = null,
    val opened: Boolean = false,
    val lowStockAt: BigDecimal? = null,
    val notes: String = "",
) {
    init {
        require(name.isNotBlank()) { "Item name must not be blank" }
        require(lowStockAt == null || lowStockAt.signum() >= 0) { "Low-stock threshold must not be negative" }
    }

    val isLowStock: Boolean
        get() = lowStockAt != null && quantity.amount <= lowStockAt

    fun expiryStatus(today: LocalDate, warningDays: Int = DEFAULT_WARNING_DAYS): ExpiryStatus =
        expiryStatus(expires, today, warningDays)

    companion object {
        /** Soonest expiry first, undated items last; ties by name, ignoring case. */
        val BySoonestExpiry: Comparator<PantryItem> =
            compareBy<PantryItem, LocalDate?>(nullsLast()) { it.expires }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
}
