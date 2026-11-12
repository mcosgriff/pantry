package io.cosgriff.pantry.domain

import io.cosgriff.pantry.domain.MeasureSystem.Metric
import io.cosgriff.pantry.domain.MeasureSystem.UsCustomary
import io.cosgriff.pantry.domain.MeasureUnit.Cup
import io.cosgriff.pantry.domain.MeasureUnit.Dozen
import io.cosgriff.pantry.domain.MeasureUnit.Each
import io.cosgriff.pantry.domain.MeasureUnit.Gallon
import io.cosgriff.pantry.domain.MeasureUnit.Gram
import io.cosgriff.pantry.domain.MeasureUnit.Kilogram
import io.cosgriff.pantry.domain.MeasureUnit.Liter
import io.cosgriff.pantry.domain.MeasureUnit.Milliliter
import io.cosgriff.pantry.domain.MeasureUnit.Ounce
import io.cosgriff.pantry.domain.MeasureUnit.Pound
import io.cosgriff.pantry.domain.MeasureUnit.Tablespoon
import io.cosgriff.pantry.domain.MeasureUnit.Teaspoon
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {
    private fun m(amount: String, unit: MeasureUnit) = Measure(BigDecimal(amount), unit)

    private fun units(system: MeasureSystem, group: UnitGroup) =
        unitChoices(system).first { it.group == group }.units.map { (it as? AmountUnit.Measured)?.unit ?: it }

    @Test
    fun `the pickers list count, volume, weight and packages, with every unit and package once`() {
        val choices = unitChoices(UsCustomary)
        assertEquals(UnitGroup.entries, choices.map { it.group })
        assertEquals(
            MeasureUnit.entries.map { AmountUnit.Measured(it) }.toSet() + Package.entries.map { AmountUnit.Packages(it) },
            choices.flatMap { it.units }.toSet(),
        )
        assertEquals(MeasureUnit.entries.size + Package.entries.size, choices.sumOf { it.units.size })
    }

    @Test
    fun `the chosen system's units come first`() {
        assertEquals(listOf(Each, Dozen), units(UsCustomary, UnitGroup.Count))
        assertEquals(listOf(Ounce, Pound, Gram, Kilogram), units(UsCustomary, UnitGroup.Weight))
        assertEquals(listOf(Gram, Kilogram, Ounce, Pound), units(Metric, UnitGroup.Weight))
        assertEquals(listOf(Milliliter, Liter), units(Metric, UnitGroup.Volume).take(2))
        assertEquals(Teaspoon, units(UsCustomary, UnitGroup.Volume).first())
        assertEquals(listOf(Milliliter, Liter), units(UsCustomary, UnitGroup.Volume).takeLast(2))
    }

    @Test
    fun `a package's contents can be in any unit, the chosen system's first`() {
        assertEquals(listOf(Each, Dozen, Milliliter, Liter), contentsUnits(Metric).take(4))
        assertEquals(MeasureUnit.entries.toSet(), contentsUnits(UsCustomary).toSet())
    }

    @Test
    fun `readable picks the unit that reads best in the system`() {
        assertEquals(m("8", Ounce), readable(m("0.5", Pound), UsCustomary))
        assertEquals(m("1.5", Pound), readable(m("24", Ounce), UsCustomary))
        assertEquals(m("1.5", Kilogram), readable(m("1500", Gram), Metric))
        assertEquals(m("227", Gram), readable(m("0.5", Pound), Metric))
        assertEquals(m("0.25", Cup), readable(m("4", Tablespoon), UsCustomary))
        assertEquals(m("3", Tablespoon), readable(m("0.1875", Cup), UsCustomary))
        assertEquals(m("2", Tablespoon), readable(m("6", Teaspoon), UsCustomary))
        assertEquals(m("2", Gallon), readable(m("8", MeasureUnit.Quart), UsCustomary))
        assertEquals(m("473", Milliliter), readable(m("2", Cup), Metric))
        assertEquals(m("18", Each), readable(m("18", Each), Metric))
    }

    @Test
    fun `a figure in another dimension through a density is approximate`() {
        val flour = densityOf("all-purpose flour")!!
        val shown = comparedFigure(m("2", Cup), Packaged(BigDecimal.ONE, Package.Bag, m("5", Pound)), UsCustomary, flour)!!
        assertEquals(true, shown.approximate)
        assertEquals(Ounce, shown.measure.unit)
        val metric = comparedFigure(m("2", Cup), m("2", Kilogram), Metric, flour)!!
        assertEquals(Gram, metric.measure.unit)
        assertEquals(true, metric.approximate)
    }

    @Test
    fun `a figure in another system is exact`() {
        assertEquals(Shown(m("1.1", Pound), approximate = false), comparedFigure(m("500", Gram), m("5", Pound), UsCustomary))
        assertEquals(Shown(m("12", Each), approximate = false), comparedFigure(m("1", Dozen), m("18", Each), UsCustomary))
    }

    @Test
    fun `no figure when there is nothing to add or nothing to convert`() {
        // The same unit, or already in the system and the item's dimension.
        assertNull(comparedFigure(m("12", Each), m("18", Each), UsCustomary))
        assertNull(comparedFigure(m("1", Pound), Packaged(BigDecimal(2), Package.Bag, m("5", Pound)), UsCustomary))
        assertNull(comparedFigure(m("2", Cup), m("1", Gallon), UsCustomary))
        // Packages of one kind count as packages.
        assertNull(comparedFigure(Packaged(BigDecimal.ONE, Package.Tub), Packaged(BigDecimal(2), Package.Tub), UsCustomary))
        // No contents, no density: nothing to convert.
        assertNull(comparedFigure(m("1", Tablespoon), Packaged(BigDecimal.ONE, Package.Tub), UsCustomary))
        assertNull(comparedFigure(m("2", Cup), m("5", Pound), UsCustomary, density = null))
    }

    @Test
    fun `an ingredient's needed figure uses the item's density first`() {
        val today = LocalDate.of(2026, 11, 10)
        val honey = PantryItem(
            id = 1, name = "Test honey", location = Location.Dry, quantity = m("2", Pound), purchased = today,
            expires = null, density = Density(BigDecimal("1.5")),
        )
        val meal = Meal("Test toast", 1, listOf(Ingredient.of(honey).copy(unit = AmountUnit.Measured(Cup), amount = BigDecimal.ONE)))
        val check = checkPlan(MealPlan(meal, today), listOf(honey), today).single()
        // 1 cup is 236.6 ml; at 1.5 g/ml, 354.9 g, which is 12.5 oz.
        assertEquals(Shown(m("12.5", Ounce), approximate = true), check.neededFigure(UsCustomary))
        assertEquals(Shown(m("355", Gram), approximate = true), check.neededFigure(Metric))
    }
}
