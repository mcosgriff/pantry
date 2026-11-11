package io.cosgriff.pantry.domain

import java.math.BigDecimal

/** The units an item can be counted in (Item details board). The app supplies the wording. */
enum class QuantityUnit { Each, Pound, Ounce, Bag, Pack, Tub }

/**
 * An amount with its unit; never a bare number. [amount] is a [BigDecimal] so a store-packed weight such as
 * 1.24 lb (#13) stays exact.
 */
data class Quantity(val amount: BigDecimal, val unit: QuantityUnit) {
    init {
        require(amount.signum() >= 0) { "Quantity must not be negative: $amount" }
    }
}
