package io.cosgriff.pantry.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// Importing a recipe from a link the user shares or pastes (#43, docs/adr/0003): the page's schema.org Recipe,
// each ingredient line matched to the pantry, and the user's decisions kept as rules for the next import.

/** What a recipe page holds that Pantry reads: its name, servings and ingredient lines, as written. */
data class RecipePage(val name: String, val servings: Int?, val ingredients: List<String>)

/** What reading a link gave. */
sealed interface RecipeResult {
    data class Found(val recipe: RecipePage) : RecipeResult

    /** The page was read, but has no schema.org Recipe with ingredients. Pantry says so; it never guesses. */
    data object NoRecipe : RecipeResult

    /** Not an `https` link, so nothing was fetched. */
    data object NotALink : RecipeResult

    /** No answer, an error, or too slow. */
    data object Failed : RecipeResult
}

/** The first web link in shared text ("Try this https://example.com/r/1 !"), or `null`. */
fun firstWebLink(text: String): String? = WEB_LINK.find(text)?.value?.trimEnd('.', ',', ')', '!', '?', ';', '"', '\'')

/** Whether [link] is one Pantry fetches: `https` with a host, nothing else (docs/adr/0003). */
fun isFetchable(link: String): Boolean = HTTPS_LINK.matches(link.trim())

/** Servings from `recipeYield` text: "4", "4 servings", "Serves 6-8" (the first number); `null` for none. */
fun servingsFrom(yield: String): Int? = Regex("""\d+""").find(yield)?.value?.toIntOrNull()?.takeIf { it in 1..MAX_SERVINGS }

/** The contents of each `<script type="application/ld+json">` in [html], in page order. */
fun jsonLdBlocks(html: String): List<String> = JSON_LD.findAll(html).map { it.groupValues[1].trim() }.filter(String::isNotEmpty).toList()

/** Text from JSON-LD as a person reads it: HTML tags dropped, entities decoded, spaces collapsed. */
fun plainText(s: String): String {
    val noTags = s.replace(TAG, " ")
    val decoded = ENTITY.replace(noTags) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let(::codePoint)
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let(::codePoint)
            else -> NAMED_ENTITIES[e]
        } ?: m.value
    }
    return decoded.replace(Regex("""\s+"""), " ").trim()
}

private fun codePoint(c: Int): String? = if (Character.isValidCodePoint(c)) String(Character.toChars(c)) else null

/**
 * What the user decided on earlier imports, applied to later ones: what a unit word means ("clove" is each),
 * and what an ingredient's name is in the pantry, or that it isn't tracked. Keys are [unitKey] and [nameKey].
 */
data class ImportRules(val units: Map<String, LineUnit> = emptyMap(), val names: Map<String, NameRule> = emptyMap()) {
    fun withUnit(word: String, unit: LineUnit) = copy(units = units + (unitKey(word) to unit))

    fun withName(name: String, rule: NameRule) = copy(names = names + (nameKey(name) to rule))

    /** The rule for an ingredient named [name], if one was learned. */
    fun nameRule(name: String): NameRule? = names[nameKey(name)]

    companion object {
        /** "Cloves", "lb." → "clove", "lb". */
        fun unitKey(word: String): String = singular(word.trim().trimEnd('.').lowercase())

        /** "Black Beans" → "black bean": lower case, words only, each singular. */
        fun nameKey(name: String): String = words(name).joinToString(" ")
    }
}

/** A learned decision about an ingredient name. */
sealed interface NameRule {
    /** It is the pantry item named [itemName]. */
    data class Item(val itemName: String) : NameRule

    data object NotTracked : NameRule
}

/** How an imported line stands against the pantry. */
sealed interface LineMatch {
    /** [learned]: from a rule the user made; otherwise a suggestion to confirm. */
    data class Item(val item: PantryItem, val learned: Boolean = false) : LineMatch

    data class NotTracked(val learned: Boolean = false) : LineMatch

    /** Nothing in the pantry looks like it: the review asks. */
    data object None : LineMatch
}

/**
 * The pantry item an ingredient called [name] is: a learned rule first, then the item whose name's words are
 * all in [name] ("Flour" for "all-purpose flour"), the most words winning, then the soonest to expire (expired
 * last). [LineMatch.None] when nothing fits.
 */
fun suggestMatch(name: String, items: List<PantryItem>, rules: ImportRules, today: LocalDate): LineMatch {
    val order = compareBy<PantryItem> { it.expires?.isBefore(today) == true }.then(PantryItem.BySoonestExpiry)
    when (val rule = rules.nameRule(name)) {
        NameRule.NotTracked -> return LineMatch.NotTracked(learned = true)
        is NameRule.Item -> {
            val key = ImportRules.nameKey(rule.itemName)
            items.filter { ImportRules.nameKey(it.name) == key }.sortedWith(order).firstOrNull()?.let { return LineMatch.Item(it, learned = true) }
        }
        null -> Unit
    }
    val lineWords = words(name).toSet() - IGNORED
    if (lineWords.isEmpty()) return LineMatch.None
    return items
        .mapNotNull { item ->
            val itemWords = words(item.name).toSet() - IGNORED
            if (itemWords.isNotEmpty() && lineWords.containsAll(itemWords)) item to itemWords.size else null
        }
        .sortedWith(compareByDescending<Pair<PantryItem, Int>> { it.second }.thenBy(order) { it.first })
        .firstOrNull()?.let { LineMatch.Item(it.first) } ?: LineMatch.None
}

/**
 * A reviewed line as a meal's ingredient (#19). A pantry match takes the item's unit, with the amount when it
 * converts exactly ("15 oz" of an item counted in lb); otherwise "some". A line not tracked keeps its amount
 * when a meal's unit can hold it, and otherwise is named as written ("2 cups flour"): meals don't yet store
 * cups (docs/adr/0002, 0003).
 */
fun importedIngredient(name: String, amountText: String?, unitWord: String?, unit: LineUnit?, contents: Measure?, match: LineMatch): Ingredient {
    val amount = amountText?.let(::parseAmount)
    val cleanName = name.trim()
    if (match is LineMatch.Item) {
        val item = match.item
        return Ingredient.of(item).copy(amount = amount?.let { amountIn(it, unit, contents, item.quantity.unit) })
    }
    val held = unit?.let(::quantityUnit)
    return when {
        amount == null -> Ingredient.typed(cleanName)
        held != null && (unit !is LineUnit.In || contents == null) -> Ingredient(cleanName, unit = held, amount = amount)
        else -> Ingredient.typed(listOfNotNull(amountText.trim(), unitWord?.trim(), cleanName).joinToString(" "))
    }
}

/** [amount] of [unit] (with [contents] for a package) in [target], when that converts exactly; else `null`. */
fun amountIn(amount: BigDecimal, unit: LineUnit?, contents: Measure?, target: QuantityUnit): BigDecimal? {
    if (unit == null) return null
    val value = when (target) {
        QuantityUnit.Bag, QuantityUnit.Pack, QuantityUnit.Tub -> amount.takeIf { quantityUnit(unit) == target }
        else -> {
            val to = when (target) {
                QuantityUnit.Each -> MeasureUnit.Each
                QuantityUnit.Pound -> MeasureUnit.Pound
                else -> MeasureUnit.Ounce
            }
            val from: Amount = when (unit) {
                is LineUnit.Of -> Measure(amount, unit.unit)
                is LineUnit.In -> Packaged(amount, unit.pkg, contents)
            }
            (from.to(to) as? Conversion.Exact)?.measure?.amount
        }
    } ?: return null
    return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().takeIf { it.signum() > 0 }
}

/** The meal unit (#19) a line's unit is, if a meal can hold it. */
private fun quantityUnit(unit: LineUnit): QuantityUnit? = when (unit) {
    LineUnit.Of(MeasureUnit.Each) -> QuantityUnit.Each
    LineUnit.Of(MeasureUnit.Pound) -> QuantityUnit.Pound
    LineUnit.Of(MeasureUnit.Ounce) -> QuantityUnit.Ounce
    LineUnit.In(Package.Bag) -> QuantityUnit.Bag
    LineUnit.In(Package.Pack) -> QuantityUnit.Pack
    LineUnit.In(Package.Tub) -> QuantityUnit.Tub
    else -> null
}

/** Lower-case words of [s], each made singular. */
internal fun words(s: String): List<String> = s.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter(String::isNotEmpty).map(::singular)

/** A plural made singular, simply: "cloves" → "clove", "leaves" → "leaf", "berries" → "berry". */
internal fun singular(word: String): String = when {
    word in IRREGULAR -> IRREGULAR.getValue(word)
    word.length <= 3 && word != "lbs" && word != "kgs" && word != "mls" && word != "pts" && word != "qts" -> word
    word.endsWith("ies") -> word.dropLast(3) + "y"
    word.endsWith("oes") || word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("sses") -> word.dropLast(2)
    word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
    word.endsWith("s") -> word.dropLast(1)
    else -> word
}

private val IRREGULAR = mapOf("leaves" to "leaf", "loaves" to "loaf", "halves" to "half", "knives" to "knife")

/** Words that say how or how big, not what: left out when matching names. */
private val IGNORED = setOf(
    "a", "an", "and", "the", "of", "or", "for", "fresh", "large", "medium", "small", "chopped", "diced", "sliced",
    "minced", "whole", "sample",
)

private const val MAX_SERVINGS = 99
private val WEB_LINK = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
private val HTTPS_LINK = Regex("""https://[A-Za-z0-9.-]+(?::\d+)?(?:[/?#][^\s]*)?""", RegexOption.IGNORE_CASE)
private val JSON_LD = Regex(
    """<script[^>]*type\s*=\s*["']?application/ld\+json["']?[^>]*>(.*?)</script>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val TAG = Regex("""<[^>]*>""")
private val ENTITY = Regex("""&(#[xX]?[0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]*);""")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "frac12" to "½",
    "frac14" to "¼", "frac34" to "¾", "deg" to "°", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
    "ndash" to "–", "mdash" to "—", "eacute" to "é", "egrave" to "è", "ntilde" to "ñ",
)
