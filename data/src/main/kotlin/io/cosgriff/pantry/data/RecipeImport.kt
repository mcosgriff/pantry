package io.cosgriff.pantry.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.cosgriff.pantry.domain.ImportRules
import io.cosgriff.pantry.domain.LineUnit
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.NameRule
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.RecipePage
import io.cosgriff.pantry.domain.RecipeResult
import io.cosgriff.pantry.domain.isFetchable
import io.cosgriff.pantry.domain.jsonLdBlocks
import io.cosgriff.pantry.domain.plainText
import io.cosgriff.pantry.domain.servingsFrom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/** Reads the recipe on a page the user chose (#43). */
fun interface RecipeReader {
    suspend fun read(link: String): RecipeResult
}

/**
 * Reads a recipe page as decided in `docs/adr/0003`: one GET of the `https` link the user gave, with the app's
 * `User-Agent`, and only the page's schema.org `Recipe` JSON-LD is read from it: name, servings and ingredient
 * lines. A page without one is [RecipeResult.NoRecipe]; Pantry doesn't guess from the page's text. Any failure
 * is [RecipeResult.Failed], never an exception.
 */
class JsonLdRecipeReader(private val http: HttpGet, private val userAgent: String) : RecipeReader {
    override suspend fun read(link: String): RecipeResult {
        val url = link.trim()
        if (!isFetchable(url)) return RecipeResult.NotALink
        val (code, body) = withContext(Dispatchers.IO) { http.get(url, userAgent) } ?: return RecipeResult.Failed
        if (code !in 200..299) return RecipeResult.Failed
        return recipeIn(body)?.let(RecipeResult::Found) ?: RecipeResult.NoRecipe
    }

    companion object {
        /** The first schema.org Recipe with ingredients in [html]'s JSON-LD, or `null`. */
        fun recipeIn(html: String): RecipePage? = jsonLdBlocks(html).firstNotNullOfOrNull { block ->
            val json = try {
                JSONTokener(block).nextValue()
            } catch (_: JSONException) {
                null
            }
            recipes(json).firstNotNullOfOrNull(::page)
        }

        /** Every Recipe object in [value]: itself, an array's items, a `@graph`, or a page's `mainEntity`. */
        private fun recipes(value: Any?): List<JSONObject> = when (value) {
            is JSONArray -> (0 until value.length()).flatMap { recipes(value.opt(it)) }
            is JSONObject -> (if (isRecipe(value)) listOf(value) else emptyList()) +
                recipes(value.opt("@graph")) + recipes(value.opt("mainEntity"))
            else -> emptyList()
        }

        private fun isRecipe(json: JSONObject): Boolean = strings(json.opt("@type")).any { it.substringAfterLast('/').substringAfterLast(':') == "Recipe" }

        private fun page(recipe: JSONObject): RecipePage? {
            val lines = strings(recipe.opt("recipeIngredient") ?: recipe.opt("ingredients")).map(::plainText).filter(String::isNotBlank)
            if (lines.isEmpty()) return null
            val name = strings(recipe.opt("name")).firstOrNull()?.let(::plainText).orEmpty()
            return RecipePage(name, servings(recipe.opt("recipeYield")), lines)
        }

        /** `recipeYield` is a number, text ("4 servings") or a list of either. */
        private fun servings(value: Any?): Int? = when (value) {
            is Number -> value.toInt().takeIf { it >= 1 }?.let { servingsFrom(it.toString()) }
            else -> strings(value).firstNotNullOfOrNull(::servingsFrom)
        }

        private fun strings(value: Any?): List<String> = when (value) {
            is String -> listOf(value)
            is Number -> listOf(value.toString())
            is JSONArray -> (0 until value.length()).flatMap { strings(value.opt(it)) }
            else -> emptyList()
        }
    }
}

/** What earlier imports taught (#43): what unit words mean and which pantry item an ingredient is. */
interface ImportRulesStore {
    fun rules(): Flow<ImportRules>

    suspend fun learnUnit(word: String, unit: LineUnit)

    suspend fun learnName(name: String, rule: NameRule)
}

/**
 * [ImportRulesStore] in the settings DataStore, as one JSON value: no database table, so no schema change.
 * A rule the app no longer understands is skipped, never fails the import.
 */
@Singleton
class DataStoreImportRules @Inject constructor(private val store: DataStore<Preferences>) : ImportRulesStore {
    override fun rules(): Flow<ImportRules> = store.data.map { prefs -> prefs[RULES]?.let(::decode) ?: ImportRules() }

    override suspend fun learnUnit(word: String, unit: LineUnit) = update { it.withUnit(word, unit) }

    override suspend fun learnName(name: String, rule: NameRule) = update { it.withName(name, rule) }

    private suspend fun update(change: (ImportRules) -> ImportRules) {
        store.edit { prefs -> prefs[RULES] = encode(change(prefs[RULES]?.let(::decode) ?: ImportRules())) }
    }

    internal companion object {
        private val RULES = stringPreferencesKey("recipe_import_rules")

        fun encode(rules: ImportRules): String = JSONObject()
            .put("units", JSONObject().apply { rules.units.forEach { (word, unit) -> put(word, unitName(unit)) } })
            .put("names", JSONObject().apply { rules.names.forEach { (name, rule) -> put(name, ruleName(rule)) } })
            .toString()

        fun decode(json: String): ImportRules = try {
            val root = JSONObject(json)
            ImportRules(
                units = root.optJSONObject("units")?.let { o -> o.keys().asSequence().mapNotNull { k -> unitOf(o.optString(k))?.let { k to it } }.toMap() }.orEmpty(),
                names = root.optJSONObject("names")?.let { o -> o.keys().asSequence().mapNotNull { k -> ruleOf(o.optString(k))?.let { k to it } }.toMap() }.orEmpty(),
            )
        } catch (_: JSONException) {
            ImportRules()
        }

        private fun unitName(unit: LineUnit) = when (unit) {
            is LineUnit.Of -> "unit:${unit.unit.name}"
            is LineUnit.In -> "package:${unit.pkg.name}"
        }

        private fun unitOf(s: String): LineUnit? = when {
            s.startsWith("unit:") -> MeasureUnit.entries.firstOrNull { it.name == s.removePrefix("unit:") }?.let(LineUnit::Of)
            s.startsWith("package:") -> Package.entries.firstOrNull { it.name == s.removePrefix("package:") }?.let(LineUnit::In)
            else -> null
        }

        private fun ruleName(rule: NameRule) = when (rule) {
            is NameRule.Item -> "item:${rule.itemName}"
            NameRule.NotTracked -> "not-tracked"
        }

        private fun ruleOf(s: String): NameRule? = when {
            s == "not-tracked" -> NameRule.NotTracked
            s.startsWith("item:") && s.length > "item:".length -> NameRule.Item(s.removePrefix("item:"))
            else -> null
        }
    }
}
