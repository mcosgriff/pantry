package io.cosgriff.pantry.domain

import io.cosgriff.pantry.domain.MeasureUnit.Cup
import io.cosgriff.pantry.domain.MeasureUnit.Dozen
import io.cosgriff.pantry.domain.MeasureUnit.Each
import io.cosgriff.pantry.domain.MeasureUnit.FluidOunce
import io.cosgriff.pantry.domain.MeasureUnit.Gallon
import io.cosgriff.pantry.domain.MeasureUnit.Gram
import io.cosgriff.pantry.domain.MeasureUnit.Kilogram
import io.cosgriff.pantry.domain.MeasureUnit.Liter
import io.cosgriff.pantry.domain.MeasureUnit.Milliliter
import io.cosgriff.pantry.domain.MeasureUnit.Ounce
import io.cosgriff.pantry.domain.MeasureUnit.Pint
import io.cosgriff.pantry.domain.MeasureUnit.Pound
import io.cosgriff.pantry.domain.MeasureUnit.Quart
import io.cosgriff.pantry.domain.MeasureUnit.Tablespoon
import io.cosgriff.pantry.domain.MeasureUnit.Teaspoon
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountsTest {
    private fun m(amount: String, unit: MeasureUnit) = Measure(BigDecimal(amount), unit)

    /** Equal as numbers, to 12 decimal places: conversions through a division carry 16 significant digits. */
    private fun assertAbout(expected: String, actual: BigDecimal) {
        val diff = BigDecimal(expected).subtract(actual).abs()
        assertTrue("expected $expected, was $actual", diff < BigDecimal("1e-12").max(BigDecimal(expected).abs() * BigDecimal("1e-14")))
    }

    private fun exact(c: Conversion): Measure = (c as Conversion.Exact).measure

    private fun approximate(c: Conversion): Measure = (c as Conversion.Approximate).measure

    @Test
    fun `each unit's factor is its legal definition`() {
        // US customary volume: 1 gal = 231 in³ = 3.785411784 l exactly; the rest are fixed fractions of it.
        assertEquals(0, BigDecimal("3785.411784").compareTo(Gallon.toBase))
        assertEquals(0, BigDecimal("3785.411784").divide(BigDecimal(4)).compareTo(Quart.toBase))
        assertEquals(0, BigDecimal("3785.411784").divide(BigDecimal(8)).compareTo(Pint.toBase))
        assertEquals(0, BigDecimal("3785.411784").divide(BigDecimal(16)).compareTo(Cup.toBase))
        assertEquals(0, BigDecimal("3785.411784").divide(BigDecimal(128)).compareTo(FluidOunce.toBase))
        assertEquals(0, BigDecimal("3785.411784").divide(BigDecimal(256)).compareTo(Tablespoon.toBase))
        assertEquals(0, BigDecimal("3785.411784").divide(BigDecimal(768)).compareTo(Teaspoon.toBase))
        // Avoirdupois: 1 lb = 453.59237 g exactly; 1 oz = 1/16 lb.
        assertEquals(0, BigDecimal("453.59237").compareTo(Pound.toBase))
        assertEquals(0, BigDecimal("453.59237").divide(BigDecimal(16)).compareTo(Ounce.toBase))
        assertEquals(0, BigDecimal("1000").compareTo(Liter.toBase))
        assertEquals(0, BigDecimal("1000").compareTo(Kilogram.toBase))
        assertEquals(0, BigDecimal("12").compareTo(Dozen.toBase))
    }

    @Test
    fun `units know their dimension and system`() {
        assertEquals(Dimension.Volume, Cup.dimension)
        assertEquals(MeasureSystem.UsCustomary, Cup.system)
        assertEquals(Dimension.Mass, Gram.dimension)
        assertEquals(MeasureSystem.Metric, Gram.system)
        assertEquals(Dimension.Count, Dozen.dimension)
        assertNull(Each.system)
    }

    @Test
    fun `within a dimension conversion is exact`() {
        assertAbout("16", exact(m("1", Cup).to(Tablespoon)).amount)
        assertAbout("3", exact(m("1", Tablespoon).to(Teaspoon)).amount)
        assertAbout("1", exact(m("16", Ounce).to(Pound)).amount)
        assertAbout("2.204622621848776", exact(m("1", Kilogram).to(Pound)).amount)
        assertAbout("24", exact(m("2", Dozen).to(Each)).amount)
        assertAbout("473.176473", exact(m("2", Cup).to(Milliliter)).amount)
    }

    @Test
    fun `round trips within a dimension come back`() {
        for (from in MeasureUnit.entries) for (to in MeasureUnit.entries.filter { it.dimension == from.dimension }) {
            val there = exact(m("3.7", from).to(to))
            assertAbout("3.7", exact(there.to(from)).amount)
        }
    }

    @Test
    fun `volume and mass convert through a density, and not without one`() {
        val flour = Density(BigDecimal("0.5"))
        val grams = approximate(m("2", Cup).to(Gram, flour))
        assertAbout("236.58823650", grams.amount)
        assertAbout("2", approximate(grams.to(Cup, flour)).amount)
        assertEquals(Conversion.NotDefined, m("2", Cup).to(Gram))
    }

    @Test
    fun `count never converts to volume or mass`() {
        assertEquals(Conversion.NotDefined, m("3", Each).to(Gram, Density(BigDecimal.ONE)))
        assertEquals(Conversion.NotDefined, m("1", Liter).to(Dozen, Density(BigDecimal.ONE)))
    }

    @Test
    fun `a packaged amount converts through its contents`() {
        val flour = Packaged(BigDecimal("2"), Package.Bag, m("5", Pound))
        assertAbout("10", exact(flour.to(Pound)).amount)
        assertAbout("160", exact(flour.to(Ounce)).amount)
        assertEquals(Conversion.NotDefined, Packaged(BigDecimal("1"), Package.Tub).to(Gram))
    }

    @Test
    fun `taking from a packaged amount leaves fractional packages`() {
        val rice = Packaged(BigDecimal("2"), Package.Bag, m("2", Pound))
        val left = rice.take(m("1", Pound)) as Taken.Left
        val packaged = left.amount as Packaged
        assertAbout("1.5", packaged.count)
        assertEquals(Package.Bag, packaged.pkg)
        assertEquals(m("2", Pound), packaged.contents)
        assertEquals(false, left.approximate)
    }

    @Test
    fun `taking from a measure leaves it in its own unit, never below zero`() {
        val milk = (m("1", Liter).take(m("1", Cup)) as Taken.Left).amount as Measure
        assertEquals(Liter, milk.unit)
        assertAbout("0.7634117635", milk.amount)
        val gone = (m("1", Cup).take(m("1", Liter)) as Taken.Left).amount as Measure
        assertEquals(0, gone.amount.signum())
    }

    @Test
    fun `taking across dimensions through a density is approximate`() {
        val flour = Measure(BigDecimal("5"), Pound)
        val left = flour.take(m("2", Cup), Density(BigDecimal("0.5"))) as Taken.Left
        assertTrue(left.approximate)
        // 2 cup = 473.176473 ml × 0.5 g/ml = 236.5882365 g = 0.52158778… lb
        assertAbout("4.478412221748792", (left.amount as Measure).amount)
    }

    @Test
    fun `taking what can't be compared says so`() {
        assertEquals(Taken.NotComparable, Packaged(BigDecimal("1"), Package.Tub).take(m("1", Tablespoon)))
        assertEquals(Taken.NotComparable, m("6", Each).take(m("1", Cup)))
        // Packages of the same kind with no contents count as packages.
        val left = Packaged(BigDecimal("3"), Package.Can).take(Packaged(BigDecimal("1"), Package.Can)) as Taken.Left
        assertAbout("2", (left.amount as Packaged).count)
    }

    @Test
    fun `amounts are never negative`() {
        assertTrue(runCatching { m("-1", Cup) }.isFailure)
        assertTrue(runCatching { Packaged(BigDecimal("-1"), Package.Bag) }.isFailure)
        assertTrue(runCatching { Density(BigDecimal.ZERO) }.isFailure)
    }

    @Test
    fun `density comes from the table by name, longest phrase first`() {
        val sugar = checkNotNull(densityOf("Test granulated sugar"))
        val brown = checkNotNull(densityOf("Test light BROWN sugar"))
        assertTrue(brown.gramsPerMilliliter > sugar.gramsPerMilliliter)
        assertEquals(densityOf("flour"), densityOf("Test all-purpose flour, unbleached"))
        assertNull(densityOf("Test paprika"))
        // Whole words only: buttermilk isn't butter.
        assertEquals(densityOf("milk"), densityOf("Test buttermilk"))
    }

    @Test
    fun `an item's own density wins over the table`() {
        val own = Density(BigDecimal("0.6"))
        assertEquals(own, densityOf("Test flour", own))
    }
}
