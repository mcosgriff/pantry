package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Links, JSON-LD blocks, matching and rules for importing a recipe (#43). Made-up items and pages. */
class RecipeImportTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun item(id: Long, name: String, unit: AmountUnit = AmountUnit.Measured(MeasureUnit.Each), days: Long? = 5) =
        PantryItem(name, Location.Dry, unit.of(BigDecimal.ONE), today, expires = days?.let(today::plusDays), id = id)

    private val beans = item(1, "Black beans", AmountUnit.Packages(Package.Pack))
    private val flour = item(2, "Flour", AmountUnit.Measured(MeasureUnit.Pound))
    private val eggs = item(3, "Large eggs")
    private val beef = item(4, "Ground beef", AmountUnit.Measured(MeasureUnit.Pound))
    private val pantry = listOf(beans, flour, eggs, beef)

    @Test
    fun `finds the link in shared text`() {
        assertEquals("https://recipes.example/r/123", firstWebLink("Try this! https://recipes.example/r/123."))
        assertEquals("https://recipes.example/r?id=1&x=2", firstWebLink("https://recipes.example/r?id=1&x=2"))
        assertNull(firstWebLink("no link here"))
    }

    @Test
    fun `only https links are fetched`() {
        assertTrue(isFetchable("https://recipes.example/r/1"))
        assertTrue(isFetchable(" https://recipes.example "))
        listOf("http://recipes.example/r/1", "file:///sdcard/x.html", "content://x", "recipes.example/r/1", "https://", "")
            .forEach { assertFalse(it, isFetchable(it)) }
    }

    @Test
    fun `servings come from a yield's first number`() {
        assertEquals(4, servingsFrom("4"))
        assertEquals(4, servingsFrom("4 servings"))
        assertEquals(6, servingsFrom("Serves 6-8"))
        assertNull(servingsFrom("one loaf"))
        assertNull(servingsFrom("0"))
    }

    @Test
    fun `reads every JSON-LD block, whatever its quoting and case`() {
        val html = """
            <html><head><script type="application/ld+json">{"a":1}</script>
            <SCRIPT TYPE='application/ld+json' data-x="y">
              {"b":2}
            </SCRIPT><script type="text/javascript">var x = 1;</script></head></html>
        """.trimIndent()
        assertEquals(listOf("""{"a":1}""", """{"b":2}"""), jsonLdBlocks(html))
    }

    @Test
    fun `text is read as a person reads it`() {
        assertEquals("Mac & cheese’s best", plainText("Mac &amp; cheese&#8217;s <b>best</b>"))
        assertEquals("1 cup milk", plainText("1&nbsp;cup   milk"))
        assertEquals("½ cup", plainText("&#xBD; cup"))
        assertEquals("½ tsp", plainText("&frac12; tsp"))
        assertEquals("&unknown; stays", plainText("&unknown; stays"))
    }

    @Test
    fun `suggests the item whose words are all in the line, most words first`() {
        assertEquals(LineMatch.Item(flour), suggestMatch("all-purpose flour", pantry, ImportRules(), today))
        assertEquals(LineMatch.Item(eggs), suggestMatch("large eggs", pantry, ImportRules(), today))
        assertEquals(LineMatch.Item(eggs), suggestMatch("eggs", pantry, ImportRules(), today))
        assertEquals(LineMatch.Item(beans), suggestMatch("black beans", pantry, ImportRules(), today))
        val beefAndBeans = item(5, "Ground beef and black beans")
        assertEquals(LineMatch.Item(beefAndBeans), suggestMatch("ground beef and black beans", pantry + beefAndBeans, ImportRules(), today))
        assertEquals(LineMatch.None, suggestMatch("Salt and pepper", pantry, ImportRules(), today))
        assertEquals(LineMatch.None, suggestMatch("beans", pantry, ImportRules(), today))
    }

    @Test
    fun `of two items with the same name, the soonest to expire wins, an expired one last`() {
        val old = item(10, "Flour", AmountUnit.Measured(MeasureUnit.Pound), days = -2)
        val soon = item(11, "Flour", AmountUnit.Measured(MeasureUnit.Pound), days = 1)
        assertEquals(LineMatch.Item(soon), suggestMatch("flour", listOf(old, flour, soon), ImportRules(), today))
    }

    @Test
    fun `a learned rule wins over a suggestion`() {
        val rules = ImportRules()
            .withName("caster sugar", NameRule.Item("Sugar"))
            .withName("Salt and pepper", NameRule.NotTracked)
            .withName("all purpose flour", NameRule.NotTracked)
        val sugar = item(6, "Sugar")
        assertEquals(LineMatch.Item(sugar, learned = true), suggestMatch("Caster Sugar", pantry + sugar, rules, today))
        assertEquals(LineMatch.NotTracked(learned = true), suggestMatch("salt and pepper", pantry, rules, today))
        assertEquals(LineMatch.NotTracked(learned = true), suggestMatch("all-purpose flour", pantry, rules, today))
        // The item a rule names is gone: back to suggesting.
        assertEquals(LineMatch.None, suggestMatch("caster sugar", pantry, rules, today))
    }

    @Test
    fun `a pantry match takes the item's unit when the amount converts exactly, else keeps its own`() {
        val inBeef = importedIngredient("ground beef", "16", "oz", LineUnit.Of(MeasureUnit.Ounce), null, LineMatch.Item(beef))
        assertEquals(Ingredient.of(beef), inBeef.copy(amount = null))
        assertEquals(0, BigDecimal.ONE.compareTo(inBeef.amount))
        // 2 cups of flour counted in lb: no exact conversion, so the line's own 2 cups, compared at meal prep.
        val inFlour = importedIngredient("flour", "2", "cups", LineUnit.Of(MeasureUnit.Cup), null, LineMatch.Item(flour))
        assertEquals(flour.id, inFlour.itemId)
        assertEquals(AmountUnit.Measured(MeasureUnit.Cup), inFlour.unit)
        assertEquals(0, BigDecimal(2).compareTo(inFlour.amount))
        // A can of beans against an item counted in packs: the line's own can.
        val inBeans = importedIngredient("black beans", "1", "can", LineUnit.In(Package.Can), null, LineMatch.Item(beans))
        assertEquals(AmountUnit.Packages(Package.Can), inBeans.unit)
        assertEquals(0, BigDecimal.ONE.compareTo(inBeans.amount))
        assertEquals(0, BigDecimal(3).compareTo(importedIngredient("eggs", "3", null, LineUnit.Of(MeasureUnit.Each), null, LineMatch.Item(eggs)).amount))
        assertEquals(eggs.id, importedIngredient("eggs", "3", null, LineUnit.Of(MeasureUnit.Each), null, LineMatch.Item(eggs)).itemId)
        // An amount with no unit Pantry knows: the item, "some".
        assertEquals(Ingredient.of(flour), importedIngredient("flour", "2", "scoops", null, null, LineMatch.Item(flour)))
    }

    @Test
    fun `a line not tracked keeps its amount and unit, or is named as written`() {
        assertEquals(
            Ingredient("chicken thighs", unit = AmountUnit.Measured(MeasureUnit.Pound), amount = BigDecimal(2)),
            importedIngredient("chicken thighs", "2", "lbs", LineUnit.Of(MeasureUnit.Pound), null, LineMatch.None),
        )
        assertEquals(
            Ingredient("flour", unit = AmountUnit.Measured(MeasureUnit.Cup), amount = BigDecimal("2.5")),
            importedIngredient("flour", "2 1/2", "cups", LineUnit.Of(MeasureUnit.Cup), null, LineMatch.NotTracked()),
        )
        val can = Measure(BigDecimal(15), MeasureUnit.Ounce)
        assertEquals(
            Ingredient("black beans", unit = AmountUnit.Packages(Package.Can, can), amount = BigDecimal.ONE),
            importedIngredient("black beans", "1", "can", LineUnit.In(Package.Can), can, LineMatch.NotTracked()),
        )
        assertEquals(Ingredient.typed("Salt and pepper"), importedIngredient("Salt and pepper", null, null, null, null, LineMatch.NotTracked()))
        assertEquals(Ingredient.typed("2 cloves garlic"), importedIngredient("garlic", "2", "cloves", null, null, LineMatch.None))
    }

    @Test
    fun `amounts convert into a meal's unit only exactly`() {
        val pound = AmountUnit.Measured(MeasureUnit.Pound)
        assertEquals(0, BigDecimal("0.5").compareTo(amountIn(BigDecimal(8), LineUnit.Of(MeasureUnit.Ounce), null, pound)))
        assertEquals(0, BigDecimal("0.938").compareTo(amountIn(BigDecimal(1), LineUnit.In(Package.Can), Measure(BigDecimal(15), MeasureUnit.Ounce), pound)))
        assertEquals(0, BigDecimal(2).compareTo(amountIn(BigDecimal(2), LineUnit.In(Package.Bag), null, AmountUnit.Packages(Package.Bag))))
        assertEquals(0, BigDecimal(12).compareTo(amountIn(BigDecimal(1), LineUnit.Of(MeasureUnit.Dozen), null, AmountUnit.Measured(MeasureUnit.Each))))
        assertNull(amountIn(BigDecimal(1), LineUnit.Of(MeasureUnit.Cup), null, AmountUnit.Measured(MeasureUnit.Ounce)))
        assertNull(amountIn(BigDecimal(1), LineUnit.In(Package.Can), null, AmountUnit.Packages(Package.Pack)))
        assertNull(amountIn(BigDecimal(1), null, null, AmountUnit.Measured(MeasureUnit.Each)))
    }

    @Test
    fun `rule keys ignore case, punctuation and plurals`() {
        assertEquals("black bean", ImportRules.nameKey("Black Beans"))
        assertEquals("all purpose flour", ImportRules.nameKey("all-purpose flour"))
        assertEquals("clove", ImportRules.unitKey("Cloves"))
        assertEquals("lb", ImportRules.unitKey("lbs."))
        assertEquals("leaf", ImportRules.unitKey("leaves"))
        assertEquals("pinch", ImportRules.unitKey("pinches"))
    }
}
