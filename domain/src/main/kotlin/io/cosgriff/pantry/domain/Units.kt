package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// What the unit pickers list, and how a converted amount is shown (#57, docs/adr/0002).

/** The unit pickers' groups, in the order they are listed. */
enum class UnitGroup { Count, Volume, Weight, Package }

/** One group of a unit picker: its units, the chosen measurement system's first. */
data class UnitChoices(val group: UnitGroup, val units: List<AmountUnit>)

/**
 * Every unit and package, grouped by count, volume, weight and package. Within volume and weight, [system]'s units
 * come first, each system smallest first. Packages are listed with no contents; the picker asks for those.
 */
fun unitChoices(system: MeasureSystem): List<UnitChoices> {
    fun of(dimension: Dimension) = MeasureUnit.entries
        .filter { it.dimension == dimension }
        .sortedBy { if (it.system == null || it.system == system) 0 else 1 }
        .map { AmountUnit.Measured(it) }
    return listOf(
        UnitChoices(UnitGroup.Count, of(Dimension.Count)),
        UnitChoices(UnitGroup.Volume, of(Dimension.Volume)),
        UnitChoices(UnitGroup.Weight, of(Dimension.Mass)),
        UnitChoices(UnitGroup.Package, Package.entries.map { AmountUnit.Packages(it) }),
    )
}

/** The units a package's contents can be given in, [system]'s first: what one bag or can holds. */
fun contentsUnits(system: MeasureSystem): List<MeasureUnit> =
    unitChoices(system).filter { it.group != UnitGroup.Package }.flatMap { c -> c.units.map { (it as AmountUnit.Measured).unit } }

/**
 * A converted figure to show. [approximate] when it went through a density, so is an estimate: the UI marks it
 * "≈". An exact conversion within a dimension is shown plain.
 */
data class Shown(val measure: Measure, val approximate: Boolean)

/**
 * [measure] in [system]'s unit of its dimension that reads best, to 3 significant figures: 0.5 lb is 8 oz,
 * 1500 g is 1.5 kg, 0.25 cup is 4 tbsp. A count stays as it is.
 */
fun readable(measure: Measure, system: MeasureSystem): Measure {
    val dimension = measure.unit.dimension
    val base = measure.amount.multiply(measure.unit.toBase)
    val unit = if (dimension == Dimension.Count) {
        measure.unit
    } else {
        steps(dimension, system).last { (_, from) -> base >= from }.first
    }
    val amount = base.divide(unit.toBase, MathContext.DECIMAL64).round(MathContext(3, RoundingMode.HALF_UP))
    return Measure(amount.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }, unit)
}

/** Each unit of a dimension in [system] and the base amount from which it reads best, smallest first. */
private fun steps(dimension: Dimension, system: MeasureSystem): List<Pair<MeasureUnit, BigDecimal>> = when (dimension) {
    Dimension.Count -> listOf(MeasureUnit.Each to BigDecimal.ZERO)
    Dimension.Mass -> when (system) {
        MeasureSystem.UsCustomary -> listOf(MeasureUnit.Ounce to BigDecimal.ZERO, MeasureUnit.Pound to MeasureUnit.Pound.toBase)
        MeasureSystem.Metric -> listOf(MeasureUnit.Gram to BigDecimal.ZERO, MeasureUnit.Kilogram to MeasureUnit.Kilogram.toBase)
    }
    Dimension.Volume -> when (system) {
        MeasureSystem.UsCustomary -> listOf(
            MeasureUnit.Teaspoon to BigDecimal.ZERO,
            MeasureUnit.Tablespoon to MeasureUnit.Tablespoon.toBase,
            MeasureUnit.Cup to MeasureUnit.Cup.toBase.divide(BigDecimal(4)), // from ¼ cup
            MeasureUnit.Gallon to MeasureUnit.Gallon.toBase,
        )
        MeasureSystem.Metric -> listOf(MeasureUnit.Milliliter to BigDecimal.ZERO, MeasureUnit.Liter to MeasureUnit.Liter.toBase)
    }
}

/**
 * [needed] as it compares with [have], in [system], to show beside it when the two are counted in different
 * dimensions or systems: a recipe's "2 cup" of flour against a bag of "5 lb" shows as about 9 oz. `null` when
 * there is nothing to add: the same unit or package, already in [system] and [have]'s dimension, or not
 * convertible ([IngredientStatus.CantCompare] says that). Through [density] the figure is [Shown.approximate].
 */
fun comparedFigure(needed: Amount, have: Amount, system: MeasureSystem, density: Density? = null): Shown? {
    if (needed.countedIn == have.countedIn) return null
    if (needed is Packaged && have is Packaged && needed.pkg == have.pkg) return null
    val haveUnit = when (have) {
        is Measure -> have.unit
        is Packaged -> have.contents?.unit ?: return null
    }
    val neededMeasure = when (needed) {
        is Measure -> needed
        is Packaged -> needed.total ?: return null
    }
    val dimension = haveUnit.dimension
    val unit = neededMeasure.unit
    if (dimension == Dimension.Count) {
        if (unit == haveUnit) return null
    } else if (unit.dimension == dimension && unit.system == system) {
        return null
    }
    val target = if (dimension == Dimension.Count) haveUnit else steps(dimension, system).first().first
    return when (val converted = neededMeasure.to(target, density)) {
        Conversion.NotDefined -> null
        is Conversion.Exact -> Shown(readable(converted.measure, system), approximate = false)
        is Conversion.Approximate -> Shown(readable(converted.measure, system), approximate = true)
    }
}

/**
 * What this ingredient's needed amount comes to in its pantry item's terms, in [system] (#57); see
 * [comparedFigure]. Through the item's density, or the table's, it is approximate.
 */
fun IngredientCheck.neededFigure(system: MeasureSystem): Shown? {
    val have = item ?: return null
    val needed = neededAmount ?: return null
    if (status is IngredientStatus.CantCompare) return null
    return comparedFigure(needed, have.quantity, system, densityFor(ingredient, have))
}
