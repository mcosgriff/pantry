package io.cosgriff.pantry.data

import io.cosgriff.pantry.domain.AmountUnit
import io.cosgriff.pantry.domain.Density
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.Package
import java.math.BigDecimal

// How an amount is stored, the same way for items, ingredients and known products (docs/adr/0002, #56): the
// unit column holds a unit or package name, and a package's contents sit in two more columns. The names are
// the enums' own, so the old `Each`, `Pound` and `Ounce` read as units and `Bag`, `Pack` and `Tub` as packages
// with no contents.

/** The unit column: a unit's or a package's name. */
internal val AmountUnit.storedName: String
    get() = when (this) {
        is AmountUnit.Measured -> unit.name
        is AmountUnit.Packages -> pkg.name
    }

/** The contents amount column: decimal text, or `null` without contents. */
internal val AmountUnit.storedContentsAmount: String?
    get() = (this as? AmountUnit.Packages)?.contents?.amount?.toPlainString()

/** The contents unit column: a unit's name, or `null` without contents. */
internal val AmountUnit.storedContentsUnit: String?
    get() = (this as? AmountUnit.Packages)?.contents?.unit?.name

/** The unit stored as [name], with a package's contents when both contents columns are set. */
internal fun amountUnit(name: String, contentsAmount: String?, contentsUnit: String?): AmountUnit {
    MeasureUnit.entries.firstOrNull { it.name == name }?.let { return AmountUnit.Measured(it) }
    val pkg = Package.entries.firstOrNull { it.name == name } ?: error("Unknown unit: $name")
    val contents = if (contentsAmount != null && contentsUnit != null) {
        Measure(BigDecimal(contentsAmount), MeasureUnit.valueOf(contentsUnit))
    } else {
        null
    }
    return AmountUnit.Packages(pkg, contents)
}

internal fun Density.stored(): String = gramsPerMilliliter.toPlainString()

internal fun density(stored: String): Density = Density(BigDecimal(stored))
