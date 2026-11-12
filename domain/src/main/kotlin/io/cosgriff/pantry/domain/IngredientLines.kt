package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.math.RoundingMode

// Reading a recipe's ingredient lines ("1 (15 oz) can black beans, drained") into amount, unit and name (#43,
// docs/adr/0003). Recipe sites write these by hand, so the parser reads the common shapes and says when it
// doesn't know a unit word, rather than guessing.

/** What a line's unit stands for: a unit of measure, or a package (docs/adr/0002). */
sealed interface LineUnit {
    data class Of(val unit: MeasureUnit) : LineUnit

    data class In(val pkg: Package) : LineUnit

    companion object {
        /** Every choice, in the order a unit picker lists them. */
        val all: List<LineUnit> = MeasureUnit.entries.map(::Of) + Package.entries.map(::In)
    }
}

/**
 * One ingredient line as read.
 *
 * @property text the line as the recipe wrote it.
 * @property amountText the amount as written ("2 1/2", "½", "2-3"); [amount] is its value, the larger end of a
 *   range. Both `null` for a line with no amount ("Salt and pepper to taste").
 * @property unitWord the unit as written ("cups", "lb.", "cloves"); `null` when the amount counts the thing
 *   itself ("3 large eggs").
 * @property unit what [unitWord] means, or each for a counted line; `null` with a [unitWord] Pantry doesn't know
 *   yet, which the user is asked about ([unknownUnit]).
 * @property contents what one package holds, from "1 (15 oz) can".
 */
data class ParsedLine(
    val text: String,
    val name: String,
    val amount: BigDecimal? = null,
    val amountText: String? = null,
    val unitWord: String? = null,
    val unit: LineUnit? = null,
    val contents: Measure? = null,
    val note: String? = null,
) {
    /** A unit word Pantry doesn't know: the review asks what it means. */
    val unknownUnit: Boolean get() = unitWord != null && unit == null
}

/** Reads one ingredient line. A unit word [rules] have learned counts as known. */
fun parseIngredientLine(line: String, rules: ImportRules = ImportRules()): ParsedLine {
    val text = line.trim()
    var rest = normalizeFractions(text).trimStart('-', '•', '*', '·', ' ')
    val amountMatch = AMOUNT.find(rest) ?: return withName(text, rest, null)
    val amountText = amountMatch.value.trim()
    rest = rest.substring(amountMatch.range.last + 1).trimStart()

    // "1 (15 oz) can": what one package holds, before the package.
    var parenthesis: String? = null
    PARENTHESIS.find(rest)?.let { m ->
        parenthesis = m.groupValues[1].trim()
        rest = rest.substring(m.range.last + 1).trimStart()
    }
    var contents = parenthesis?.let(::parseContents)

    var unitWord: String? = null
    var unit: LineUnit? = LineUnit.Of(MeasureUnit.Each)
    unitAt(rest, rules)?.let { (word, meaning) ->
        unitWord = word
        unit = meaning
        rest = rest.substring(word.length).trimStart()
        // "1 can (15 oz) black beans": the contents after the package.
        if (parenthesis == null) {
            PARENTHESIS.find(rest)?.let { m ->
                parseContents(m.groupValues[1])?.let {
                    contents = it
                    rest = rest.substring(m.range.last + 1).trimStart()
                }
            }
        }
        // "1 cup/240 ml milk": the other system's measure is left out.
        ALTERNATIVE.find(rest)?.let { rest = rest.substring(it.range.last + 1).trimStart() }
    }
    rest = rest.removePrefix("of ").trimStart()
    // Contents only mean something for a package; otherwise "(8 oz)" stays as a note.
    val keptContents = contents?.takeIf { unit is LineUnit.In }
    val note = parenthesis?.takeIf { keptContents == null }
    return withName(text, rest, note).copy(
        amount = parseAmount(amountText),
        amountText = amountText,
        unitWord = unitWord,
        unit = unit,
        contents = keptContents,
    )
}

/** Name and note from what follows the amount and unit: the note is after the first comma, or "to taste". */
private fun withName(text: String, rest: String, extraNote: String?): ParsedLine {
    var name = rest.trim()
    val notes = mutableListOf<String>()
    extraNote?.let(notes::add)
    TRAILING_PARENTHESIS.find(name)?.let {
        notes += it.groupValues[1].trim()
        name = name.substring(0, it.range.first).trim()
    }
    val comma = name.indexOf(',')
    if (comma >= 0) {
        notes += name.substring(comma + 1).trim()
        name = name.substring(0, comma).trim()
    }
    TO_TASTE.find(name)?.let {
        notes += it.value.trim()
        name = name.substring(0, it.range.first).trim()
    }
    name = name.trim(' ', ',', ';', '.')
    return ParsedLine(text = text, name = name.ifEmpty { text }, note = notes.filter(String::isNotEmpty).joinToString(", ").ifEmpty { null })
}

/**
 * An amount as recipes write it: "2", "2.5", "2,5", "1/2", "2 1/2", "½", "1½", "1-1/2"; a range ("2-3",
 * "2 to 3") is its larger end, so shopping for it is enough. Kept to 3 decimals. `null` if it isn't one.
 */
fun parseAmount(text: String): BigDecimal? {
    val s = normalizeFractions(text).trim()
    single(s)?.let { return it.takeIf { v -> v.signum() > 0 } }
    val range = RANGE.matchEntire(s) ?: return null
    val low = single(range.groupValues[1].trim()) ?: return null
    val high = single(range.groupValues[2].trim()) ?: return null
    return low.max(high).takeIf { it.signum() > 0 }
}

private fun single(s: String): BigDecimal? {
    WHOLE_AND_FRACTION.matchEntire(s)?.let { m ->
        val whole = BigDecimal(m.groupValues[1])
        return fraction(m.groupValues[2], m.groupValues[3])?.let(whole::add)
    }
    FRACTION.matchEntire(s)?.let { m -> return fraction(m.groupValues[1], m.groupValues[2]) }
    DECIMAL.matchEntire(s)?.let { return BigDecimal(s.replace(',', '.')) }
    return null
}

private fun fraction(numerator: String, denominator: String): BigDecimal? {
    val d = BigDecimal(denominator)
    if (d.signum() == 0) return null
    return BigDecimal(numerator).divide(d, 3, RoundingMode.HALF_UP).stripTrailingZeros()
}

/** "½" → " 1/2", so "1½" reads "1 1/2"; the fraction slash becomes "/". */
private fun normalizeFractions(s: String): String {
    val out = StringBuilder()
    s.forEach { c ->
        val f = VULGAR[c]
        when {
            f != null -> out.append(' ').append(f).append(' ')
            c == '⁄' -> out.append('/')
            c == '\u00A0' -> out.append(' ')
            else -> out.append(c)
        }
    }
    return out.toString().replace(SPACES, " ").replace(Regex("""(\d)\s+/\s*(\d)"""), "$1/$2").replace(" ,", ",").trim()
}

private fun parseContents(s: String): Measure? {
    val m = CONTENTS.matchEntire(s.trim()) ?: return null
    val amount = parseAmount(m.groupValues[1]) ?: return null
    val word = m.groupValues[2].trim()
    val unit = if (TWO_WORD_UNIT.matches(word)) MeasureUnit.FluidOunce else (unitFor(word, ImportRules()) as? LineUnit.Of)?.unit ?: return null
    if (unit.dimension == Dimension.Count) return null
    return Measure(amount, unit)
}

/** The unit word at the start of [rest] and what it means (`null` for a word to ask about), or `null` for none. */
private fun unitAt(rest: String, rules: ImportRules): Pair<String, LineUnit?>? {
    TWO_WORD_UNIT.find(rest)?.let { return it.value to LineUnit.Of(MeasureUnit.FluidOunce) }
    val word = WORD.find(rest)?.value ?: return null
    rules.units[ImportRules.unitKey(word)]?.let { return word to it }
    unitFor(word, rules)?.let { return word to it }
    if (ImportRules.unitKey(word) in TO_ASK) return word to null
    return null
}

/** What a unit word means, or `null`. Single letters are read by case: "T" tablespoon, "t" teaspoon. */
internal fun unitFor(word: String, rules: ImportRules): LineUnit? {
    val bare = word.trimEnd('.')
    when (bare) {
        "T", "Tb", "Tbl" -> return LineUnit.Of(MeasureUnit.Tablespoon)
        "t" -> return LineUnit.Of(MeasureUnit.Teaspoon)
        "c", "C" -> return LineUnit.Of(MeasureUnit.Cup)
    }
    if (bare.length == 1 && bare != "g" && bare != "l" && bare != "L") return null
    val key = ImportRules.unitKey(bare)
    return rules.units[key] ?: UNITS[key]
}

private val UNITS: Map<String, LineUnit> = buildMap {
    fun of(unit: MeasureUnit, vararg words: String) = words.forEach { put(it, LineUnit.Of(unit)) }
    fun pkg(pkg: Package, vararg words: String) = words.forEach { put(it, LineUnit.In(pkg)) }
    of(MeasureUnit.Each, "each", "ea")
    of(MeasureUnit.Dozen, "dozen", "doz")
    of(MeasureUnit.Teaspoon, "tsp", "teaspoon", "teasp")
    of(MeasureUnit.Tablespoon, "tbsp", "tb", "tbl", "tablespoon", "tblsp", "tbs")
    of(MeasureUnit.FluidOunce, "floz")
    of(MeasureUnit.Cup, "cup")
    of(MeasureUnit.Pint, "pint", "pt")
    of(MeasureUnit.Quart, "quart", "qt")
    of(MeasureUnit.Gallon, "gallon", "gal")
    of(MeasureUnit.Milliliter, "ml", "milliliter", "millilitre")
    of(MeasureUnit.Liter, "l", "liter", "litre")
    of(MeasureUnit.Ounce, "oz", "ounce")
    of(MeasureUnit.Pound, "lb", "pound")
    of(MeasureUnit.Gram, "g", "gram", "gramme", "gr")
    of(MeasureUnit.Kilogram, "kg", "kilogram", "kilo")
    pkg(Package.Can, "can", "tin")
    pkg(Package.Jar, "jar")
    pkg(Package.Bag, "bag")
    pkg(Package.Box, "box")
    pkg(Package.Bottle, "bottle")
    pkg(Package.Carton, "carton")
    pkg(Package.Pack, "pack", "package", "packet", "pkg")
    pkg(Package.Tub, "tub")
}

/** Words recipes use as units that aren't units of measure: Pantry asks what one means the first time. */
private val TO_ASK = setOf(
    "clove", "pinch", "dash", "sprig", "stalk", "bunch", "head", "slice", "handful", "stick", "piece", "sheet",
    "leaf", "splash", "drop", "scoop", "envelope", "container", "knob", "rib", "fillet", "ear", "strip", "cube",
    "sachet", "loaf", "block", "bulb", "wedge",
)

private val VULGAR = mapOf(
    '½' to "1/2", '⅓' to "1/3", '⅔' to "2/3", '¼' to "1/4", '¾' to "3/4", '⅕' to "1/5", '⅖' to "2/5", '⅗' to "3/5",
    '⅘' to "4/5", '⅙' to "1/6", '⅚' to "5/6", '⅛' to "1/8", '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8",
)

private const val NUMBER = """\d+(?:[.,]\d+)?(?:[\s-]+\d+/\d+)?|\d+/\d+|\d*[.,]\d+"""
private val AMOUNT = Regex("""^(?:$NUMBER)(?:\s*(?:-|–|to|or)\s*(?:$NUMBER))?(?![\d/])""")
private val RANGE = Regex("""(.+?)\s*(?:–|-|\bto\b|\bor\b)\s*(.+)""")
private val WHOLE_AND_FRACTION = Regex("""(\d+)[\s-]+(\d+)/(\d+)""")
private val FRACTION = Regex("""(\d+)/(\d+)""")
private val DECIMAL = Regex("""\d+(?:[.,]\d+)?|\d*[.,]\d+""")
private val PARENTHESIS = Regex("""^\(([^)]*)\)""")
private val TRAILING_PARENTHESIS = Regex("""\(([^)]*)\)\s*$""")
private val CONTENTS = Regex("""((?:$NUMBER))\s*-?\s*([A-Za-z. ]+?)\.?(?:\s+(?:each|can|package|jar|bag|box))?""")
private val TWO_WORD_UNIT = Regex("""^fl(?:uid)?\.?\s*(?:oz|ounces?)\b\.?""", RegexOption.IGNORE_CASE)
private val WORD = Regex("""^[A-Za-z]+\.?(?=[\s,/()]|$)""")
private val ALTERNATIVE = Regex("""^(?:/|or\s)\s*(?:$NUMBER)\s*[A-Za-z]+\.?""")
private val TO_TASTE = Regex("""\s*\b(?:to taste|as needed|for serving|for garnish)\b.*$""", RegexOption.IGNORE_CASE)
private val SPACES = Regex("""\s+""")
