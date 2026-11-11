package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.math.MathContext

// One amount model for pantry items and recipe ingredients, with unit conversions (docs/adr/0002, #54).

/** What a unit measures. Units convert exactly within a dimension. */
enum class Dimension { Count, Volume, Mass }

/** Which system a volume or mass unit belongs to; count units belong to neither. */
enum class MeasureSystem { UsCustomary, Metric }

private val GALLON_ML = BigDecimal("3785.411784") // 231 in³, exact
private val POUND_G = BigDecimal("453.59237") // exact

/**
 * A unit, with its exact factor [toBase] to its dimension's base: one, the millilitre or the gram. "Ounce" is
 * always weight; [FluidOunce] is volume.
 */
enum class MeasureUnit(val dimension: Dimension, val system: MeasureSystem?, val toBase: BigDecimal) {
    Each(Dimension.Count, null, BigDecimal.ONE),
    Dozen(Dimension.Count, null, BigDecimal(12)),

    Teaspoon(Dimension.Volume, MeasureSystem.UsCustomary, GALLON_ML.divide(BigDecimal(768))),
    Tablespoon(Dimension.Volume, MeasureSystem.UsCustomary, GALLON_ML.divide(BigDecimal(256))),
    FluidOunce(Dimension.Volume, MeasureSystem.UsCustomary, GALLON_ML.divide(BigDecimal(128))),
    Cup(Dimension.Volume, MeasureSystem.UsCustomary, GALLON_ML.divide(BigDecimal(16))),
    Pint(Dimension.Volume, MeasureSystem.UsCustomary, GALLON_ML.divide(BigDecimal(8))),
    Quart(Dimension.Volume, MeasureSystem.UsCustomary, GALLON_ML.divide(BigDecimal(4))),
    Gallon(Dimension.Volume, MeasureSystem.UsCustomary, GALLON_ML),
    Milliliter(Dimension.Volume, MeasureSystem.Metric, BigDecimal.ONE),
    Liter(Dimension.Volume, MeasureSystem.Metric, BigDecimal(1000)),

    Ounce(Dimension.Mass, MeasureSystem.UsCustomary, POUND_G.divide(BigDecimal(16))),
    Pound(Dimension.Mass, MeasureSystem.UsCustomary, POUND_G),
    Gram(Dimension.Mass, MeasureSystem.Metric, BigDecimal.ONE),
    Kilogram(Dimension.Mass, MeasureSystem.Metric, BigDecimal(1000)),
}

/** A container things are bought in. A packaged amount counts these, with optional contents. */
enum class Package { Bag, Pack, Tub, Box, Can, Jar, Bottle, Carton }

/** Grams per millilitre, which converts volume to mass. */
data class Density(val gramsPerMilliliter: BigDecimal) {
    init {
        require(gramsPerMilliliter.signum() > 0) { "Density must be positive: $gramsPerMilliliter" }
    }
}

/** How much of an item or ingredient: a [Measure] or a [Packaged] amount. Never negative. */
sealed interface Amount {
    /** This amount in [unit]; a volume–mass conversion needs [density]. */
    fun to(unit: MeasureUnit, density: Density? = null): Conversion

    /**
     * What's left after taking [used], in this amount's own shape and unit, never below zero; or
     * [Taken.NotComparable] when the two can't be converted.
     */
    fun take(used: Amount, density: Density? = null): Taken
}

/** A number and a unit: `12 each`, `1.24 lb`, `2 cup`. */
data class Measure(val amount: BigDecimal, val unit: MeasureUnit) : Amount {
    init {
        require(amount.signum() >= 0) { "An amount must not be negative: $amount" }
    }

    override fun to(unit: MeasureUnit, density: Density?): Conversion {
        if (unit.dimension == this.unit.dimension) return Conversion.Exact(Measure(rebase(this.unit, unit), unit))
        if (density == null || Dimension.Count in setOf(unit.dimension, this.unit.dimension)) return Conversion.NotDefined
        val base = amount.multiply(this.unit.toBase)
        val otherBase = if (this.unit.dimension == Dimension.Volume) {
            base.multiply(density.gramsPerMilliliter) // ml → g
        } else {
            base.divide(density.gramsPerMilliliter, MATH) // g → ml
        }
        return Conversion.Approximate(Measure(otherBase.divide(unit.toBase, MATH), unit))
    }

    override fun take(used: Amount, density: Density?): Taken {
        val usedHere = when (used) {
            is Measure -> used.to(unit, density)
            is Packaged -> used.to(unit, density)
        }
        return when (usedHere) {
            Conversion.NotDefined -> Taken.NotComparable
            is Conversion.Exact -> Taken.Left(minus(usedHere.measure.amount), approximate = false)
            is Conversion.Approximate -> Taken.Left(minus(usedHere.measure.amount), approximate = true)
        }
    }

    private fun minus(used: BigDecimal) = Measure(amount.subtract(used).max(BigDecimal.ZERO), unit)

    private fun rebase(from: MeasureUnit, to: MeasureUnit): BigDecimal =
        if (from == to) amount else amount.multiply(from.toBase).divide(to.toBase, MATH)
}

/** [count] packages of [pkg], each holding [contents] when known: `2 bag × 5 lb`. */
data class Packaged(val count: BigDecimal, val pkg: Package, val contents: Measure? = null) : Amount {
    init {
        require(count.signum() >= 0) { "A package count must not be negative: $count" }
    }

    /** Everything in the packages, or `null` when their contents aren't known. */
    val total: Measure? get() = contents?.let { Measure(it.amount.multiply(count), it.unit) }

    override fun to(unit: MeasureUnit, density: Density?): Conversion = total?.to(unit, density) ?: Conversion.NotDefined

    override fun take(used: Amount, density: Density?): Taken {
        // Packages of the same kind count as packages, whatever is in them.
        if (used is Packaged && used.pkg == pkg && (contents == null || used.contents == null)) {
            return Taken.Left(copy(count = count.subtract(used.count).max(BigDecimal.ZERO)), approximate = false)
        }
        val inside = contents ?: return Taken.NotComparable
        return when (val left = Measure(inside.amount.multiply(count), inside.unit).take(used, density)) {
            Taken.NotComparable -> Taken.NotComparable
            is Taken.Left -> {
                val remaining = (left.amount as Measure).amount
                Taken.Left(copy(count = remaining.divide(inside.amount, MATH)), left.approximate)
            }
        }
    }
}

/** A conversion's result: exact within a dimension, approximate through a density, or not defined. */
sealed interface Conversion {
    data class Exact(val measure: Measure) : Conversion

    /** Through a density, so an estimate: shown with "≈". */
    data class Approximate(val measure: Measure) : Conversion

    data object NotDefined : Conversion
}

/** What [Amount.take] leaves. */
sealed interface Taken {
    data class Left(val amount: Amount, val approximate: Boolean) : Taken

    data object NotComparable : Taken
}

/** 16 significant digits for conversions that divide; exact factors multiply without rounding. */
private val MATH = MathContext.DECIMAL64
