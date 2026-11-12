package io.cosgriff.pantry.ui.components

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.cosgriff.pantry.R
import io.cosgriff.pantry.domain.Amount
import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.ExpiryStatus
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.Packaged
import io.cosgriff.pantry.domain.Shown
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "18 ea", "0.6 bag", "2 tubs", "2 bags × 5 lb": the amount in the user's locale, with its unit. */
@Composable
@ReadOnlyComposable
fun quantityText(quantity: Amount): String = when (quantity) {
    is Measure -> counted(quantity.amount, unitPlural(quantity.unit))
    is Packaged -> {
        val packages = counted(quantity.count, packagePlural(quantity.pkg))
        quantity.contents?.let { stringResource(R.string.amount_per_package, packages, quantityText(it)) } ?: packages
    }
}

@Composable
@ReadOnlyComposable
private fun counted(number: BigDecimal, @PluralsRes plural: Int): String {
    val amount = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }.format(number)
    // Plural rules take a whole count. A fraction takes the "other" form, as CLDR has it for English ("0.6 bags").
    val count = if (isWhole(number)) number.toInt() else 2
    return pluralStringResource(plural, count, amount)
}

/**
 * A converted figure (#57): "9 oz", or "≈ 9 oz" when it went through a density and so is an estimate. Which
 * figures are approximate is the domain's rule (`Shown.approximate`).
 */
@Composable
@ReadOnlyComposable
fun shownText(shown: Shown): String {
    val text = quantityText(shown.measure)
    return if (shown.approximate) stringResource(R.string.amount_approximate, text) else text
}

/** A unit by name ("each", "lb", "bag", "bag × 5 lb"), as the unit picker lists them. */
@Composable
@ReadOnlyComposable
fun unitName(unit: AmountUnit): String = when (unit) {
    is AmountUnit.Measured -> stringResource(unitNameRes(unit.unit))
    is AmountUnit.Packages -> {
        val name = stringResource(packageNameRes(unit.pkg))
        unit.contents?.let { stringResource(R.string.amount_per_package, name, quantityText(it)) } ?: name
    }
}

@PluralsRes
private fun unitPlural(unit: MeasureUnit): Int = when (unit) {
    MeasureUnit.Each -> R.plurals.unit_each
    MeasureUnit.Dozen -> R.plurals.unit_dozen
    MeasureUnit.Teaspoon -> R.plurals.unit_teaspoon
    MeasureUnit.Tablespoon -> R.plurals.unit_tablespoon
    MeasureUnit.FluidOunce -> R.plurals.unit_fluid_ounce
    MeasureUnit.Cup -> R.plurals.unit_cup
    MeasureUnit.Pint -> R.plurals.unit_pint
    MeasureUnit.Quart -> R.plurals.unit_quart
    MeasureUnit.Gallon -> R.plurals.unit_gallon
    MeasureUnit.Milliliter -> R.plurals.unit_milliliter
    MeasureUnit.Liter -> R.plurals.unit_liter
    MeasureUnit.Ounce -> R.plurals.unit_ounce
    MeasureUnit.Pound -> R.plurals.unit_pound
    MeasureUnit.Gram -> R.plurals.unit_gram
    MeasureUnit.Kilogram -> R.plurals.unit_kilogram
}

@PluralsRes
private fun packagePlural(pkg: Package): Int = when (pkg) {
    Package.Bag -> R.plurals.unit_bag
    Package.Pack -> R.plurals.unit_pack
    Package.Tub -> R.plurals.unit_tub
    Package.Box -> R.plurals.unit_box
    Package.Can -> R.plurals.unit_can
    Package.Jar -> R.plurals.unit_jar
    Package.Bottle -> R.plurals.unit_bottle
    Package.Carton -> R.plurals.unit_carton
}

@StringRes
private fun unitNameRes(unit: MeasureUnit): Int = when (unit) {
    MeasureUnit.Each -> R.string.unit_name_each
    MeasureUnit.Dozen -> R.string.unit_name_dozen
    MeasureUnit.Teaspoon -> R.string.unit_name_teaspoon
    MeasureUnit.Tablespoon -> R.string.unit_name_tablespoon
    MeasureUnit.FluidOunce -> R.string.unit_name_fluid_ounce
    MeasureUnit.Cup -> R.string.unit_name_cup
    MeasureUnit.Pint -> R.string.unit_name_pint
    MeasureUnit.Quart -> R.string.unit_name_quart
    MeasureUnit.Gallon -> R.string.unit_name_gallon
    MeasureUnit.Milliliter -> R.string.unit_name_milliliter
    MeasureUnit.Liter -> R.string.unit_name_liter
    MeasureUnit.Ounce -> R.string.unit_name_ounce
    MeasureUnit.Pound -> R.string.unit_name_pound
    MeasureUnit.Gram -> R.string.unit_name_gram
    MeasureUnit.Kilogram -> R.string.unit_name_kilogram
}

@StringRes
private fun packageNameRes(pkg: Package): Int = when (pkg) {
    Package.Bag -> R.string.unit_name_bag
    Package.Pack -> R.string.unit_name_pack
    Package.Tub -> R.string.unit_name_tub
    Package.Box -> R.string.unit_name_box
    Package.Can -> R.string.unit_name_can
    Package.Jar -> R.string.unit_name_jar
    Package.Bottle -> R.string.unit_name_bottle
    Package.Carton -> R.string.unit_name_carton
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
