package io.cosgriff.pantry.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ingredient lines in the styles recipe sites write them (#43). Every line here is made up for the test. */
class IngredientLinesTest {
    private fun of(unit: MeasureUnit) = LineUnit.Of(unit)

    private fun pkg(pkg: Package) = LineUnit.In(pkg)

    /** One row: the line, then what it should read as. `amount` compares as a number. */
    private data class Row(
        val line: String,
        val amount: String?,
        val unit: LineUnit?,
        val name: String,
        val unitWord: String? = null,
        val note: String? = null,
        val contents: Measure? = null,
    )

    private val rows = listOf(
        Row("1 (15 oz) can black beans, drained", "1", pkg(Package.Can), "black beans", "can", "drained", Measure(BigDecimal(15), MeasureUnit.Ounce)),
        Row("2 (14.5 ounce) cans diced tomatoes", "2", pkg(Package.Can), "diced tomatoes", "cans", contents = Measure(BigDecimal("14.5"), MeasureUnit.Ounce)),
        Row("1 (8-ounce) package cream cheese, softened", "1", pkg(Package.Pack), "cream cheese", "package", "softened", Measure(BigDecimal(8), MeasureUnit.Ounce)),
        Row("1 can (400 g) coconut milk", "1", pkg(Package.Can), "coconut milk", "can", contents = Measure(BigDecimal(400), MeasureUnit.Gram)),
        Row("2 1/2 cups all-purpose flour", "2.5", of(MeasureUnit.Cup), "all-purpose flour", "cups"),
        Row("1-1/2 cups milk", "1.5", of(MeasureUnit.Cup), "milk", "cups"),
        Row("½ tsp salt", "0.5", of(MeasureUnit.Teaspoon), "salt", "tsp"),
        Row("1½ teaspoons baking soda", "1.5", of(MeasureUnit.Teaspoon), "baking soda", "teaspoons"),
        Row("¾ cup sugar", "0.75", of(MeasureUnit.Cup), "sugar", "cup"),
        Row("1/3 cup olive oil", "0.333", of(MeasureUnit.Cup), "olive oil", "cup"),
        Row("3 large eggs", "3", of(MeasureUnit.Each), "large eggs"),
        Row("1 egg, beaten", "1", of(MeasureUnit.Each), "egg", note = "beaten"),
        Row("Salt and pepper to taste", null, null, "Salt and pepper", note = "to taste"),
        Row("1 lb. ground beef", "1", of(MeasureUnit.Pound), "ground beef", "lb."),
        Row("2 lbs chicken thighs", "2", of(MeasureUnit.Pound), "chicken thighs", "lbs"),
        Row("200g caster sugar", "200", of(MeasureUnit.Gram), "caster sugar", "g"),
        Row("500 ml chicken stock", "500", of(MeasureUnit.Milliliter), "chicken stock", "ml"),
        Row("1.5 kg potatoes, peeled", "1.5", of(MeasureUnit.Kilogram), "potatoes", "kg", "peeled"),
        Row("1 Tbsp soy sauce", "1", of(MeasureUnit.Tablespoon), "soy sauce", "Tbsp"),
        Row("2 T butter", "2", of(MeasureUnit.Tablespoon), "butter", "T"),
        Row("1 t vanilla extract", "1", of(MeasureUnit.Teaspoon), "vanilla extract", "t"),
        Row("4 fl oz heavy cream", "4", of(MeasureUnit.FluidOunce), "heavy cream", "fl oz"),
        Row("2 cups of water", "2", of(MeasureUnit.Cup), "water", "cups"),
        Row("1 cup/240 ml whole milk", "1", of(MeasureUnit.Cup), "whole milk", "cup"),
        Row("2-3 cloves garlic, minced", "3", null, "garlic", "cloves", "minced"),
        Row("2 to 3 tablespoons honey", "3", of(MeasureUnit.Tablespoon), "honey", "tablespoons"),
        Row("1 pinch red pepper flakes", "1", null, "red pepper flakes", "pinch"),
        Row("2 (8 oz) chicken breasts", "2", of(MeasureUnit.Each), "chicken breasts", note = "8 oz"),
        Row("1 onion, finely chopped", "1", of(MeasureUnit.Each), "onion", note = "finely chopped"),
        Row("1 cup shredded cheddar cheese (optional)", "1", of(MeasureUnit.Cup), "shredded cheddar cheese", "cup", "optional"),
        Row("Fresh parsley, for garnish", null, null, "Fresh parsley", note = "for garnish"),
        Row("1 dozen eggs", "1", of(MeasureUnit.Dozen), "eggs", "dozen"),
        Row("1 jar (24 oz) marinara sauce", "1", pkg(Package.Jar), "marinara sauce", "jar", contents = Measure(BigDecimal(24), MeasureUnit.Ounce)),
        Row("  - 2 carrots  ", "2", of(MeasureUnit.Each), "carrots"),
    )

    @Test
    fun `reads lines in the styles recipe sites write`() {
        rows.forEach { row ->
            val parsed = parseIngredientLine(row.line)
            val where = "\"${row.line}\" read as $parsed"
            if (row.amount == null) assertNull(where, parsed.amount) else assertEquals(where, 0, BigDecimal(row.amount).compareTo(parsed.amount))
            assertEquals(where, row.unit, parsed.unit)
            assertEquals(where, row.name, parsed.name)
            assertEquals(where, row.unitWord, parsed.unitWord)
            assertEquals(where, row.note, parsed.note)
            assertEquals(where, row.contents?.unit, parsed.contents?.unit)
            assertEquals(where, 0, (row.contents?.amount ?: BigDecimal.ZERO).compareTo(parsed.contents?.amount ?: BigDecimal.ZERO))
            assertEquals(where, row.line.trim(), parsed.text)
        }
    }

    @Test
    fun `an amount keeps the text it was written as`() {
        assertEquals("2 1/2", parseIngredientLine("2 1/2 cups flour").amountText)
        assertEquals("1/2", parseIngredientLine("½ tsp salt").amountText)
        assertEquals("2-3", parseIngredientLine("2-3 cloves garlic").amountText)
    }

    @Test
    fun `a unit word Pantry doesn't know is asked about, not guessed`() {
        val garlic = parseIngredientLine("2 cloves garlic")
        assertTrue(garlic.unknownUnit)
        assertNull(garlic.unit)
        assertFalse(parseIngredientLine("3 large eggs").unknownUnit)
        assertFalse(parseIngredientLine("Salt to taste").unknownUnit)
    }

    @Test
    fun `a learned unit word is known on the next import`() {
        val rules = ImportRules().withUnit("Cloves", LineUnit.Of(MeasureUnit.Each))
        val garlic = parseIngredientLine("2 cloves garlic", rules)
        assertEquals(LineUnit.Of(MeasureUnit.Each), garlic.unit)
        assertEquals("cloves", garlic.unitWord)
        assertEquals(LineUnit.Of(MeasureUnit.Each), parseIngredientLine("1 clove garlic", rules).unit)
    }

    @Test
    fun `amounts read as recipes write them`() {
        mapOf(
            "2" to "2", "2.5" to "2.5", "2,5" to "2.5", "1/2" to "0.5", "2 1/2" to "2.5", "½" to "0.5", "1½" to "1.5",
            "1-1/2" to "1.5", "2-3" to "3", "2 to 3" to "3", "1 ⁄ 4" to "0.25", ".5" to "0.5",
        ).forEach { (text, value) -> assertEquals(text, 0, BigDecimal(value).compareTo(parseAmount(text))) }
        listOf("", "some", "0", "1/0", "a few").forEach { assertNull(it, parseAmount(it)) }
    }
}
