package io.cosgriff.pantry.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.ExpiryStatus
import io.cosgriff.pantry.domain.Quantity
import io.cosgriff.pantry.domain.QuantityUnit
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "18 ea", "0.6 bag", "2 tubs": the amount in the user's locale, with its unit. */
@Composable
@ReadOnlyComposable
fun quantityText(quantity: Quantity): String {
    val amount = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }.format(quantity.amount)
    // Plural rules take a whole count. A fraction takes the "other" form, as CLDR has it for English ("0.6 bags").
    val count = if (isWhole(quantity.amount)) quantity.amount.toInt() else 2
    val plural = when (quantity.unit) {
        QuantityUnit.Each -> R.plurals.unit_each
        QuantityUnit.Pound -> R.plurals.unit_pound
        QuantityUnit.Ounce -> R.plurals.unit_ounce
        QuantityUnit.Bag -> R.plurals.unit_bag
        QuantityUnit.Pack -> R.plurals.unit_pack
        QuantityUnit.Tub -> R.plurals.unit_tub
    }
    return pluralStringResource(plural, count, amount)
}

/** Whether [amount] is a whole number, for choosing a plural form. */
internal fun isWhole(amount: BigDecimal): Boolean = amount.signum() == 0 || amount.stripTrailingZeros().scale() <= 0

/**
 * The expiry line's first part: "Expires in 2 days", "Expired yesterday", or the date itself for an item well
 * ahead of its date, in the user's locale.
 */
@Composable
@ReadOnlyComposable
fun expiryText(status: ExpiryStatus, expires: LocalDate?): String = when (status) {
    ExpiryStatus.NoDate -> stringResource(R.string.expiry_no_date)
    is ExpiryStatus.Expired -> pluralStringResource(R.plurals.expiry_days_ago, status.daysAgo.toInt(), status.daysAgo)
    is ExpiryStatus.ExpiresSoon ->
        if (status.daysLeft == 0L) {
            stringResource(R.string.expiry_today)
        } else {
            pluralStringResource(R.plurals.expiry_in_days, status.daysLeft.toInt(), status.daysLeft)
        }
    is ExpiryStatus.Fine -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(checkNotNull(expires))
}
