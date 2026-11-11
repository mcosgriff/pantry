package io.cosgriff.pantry.ui.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.pantry.data.ImportRulesStore
import io.cosgriff.pantry.data.MealRepository
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.RecipeReader
import io.cosgriff.pantry.domain.ImportRules
import io.cosgriff.pantry.domain.LineMatch
import io.cosgriff.pantry.domain.LineUnit
import io.cosgriff.pantry.domain.Meal
import io.cosgriff.pantry.domain.Measure
import io.cosgriff.pantry.domain.NameRule
import io.cosgriff.pantry.domain.PantryItem
import io.cosgriff.pantry.domain.RecipeResult
import io.cosgriff.pantry.domain.importedIngredient
import io.cosgriff.pantry.domain.parseIngredientLine
import io.cosgriff.pantry.domain.suggestMatch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One ingredient line under review: as the recipe wrote it, as read (and changed by the user), and its pantry
 * match.
 *
 * @property askedUnit the recipe's unit word wasn't known: the user is asked, and their answer is learned.
 * @property decided the user (or a learned rule) chose the match; editing the name doesn't change it.
 */
data class ImportLine(
    val text: String,
    val amountText: String,
    val unitWord: String?,
    val unit: LineUnit?,
    val name: String,
    val match: LineMatch,
    val contents: Measure? = null,
    val askedUnit: Boolean = false,
    val decided: Boolean = false,
) {
    /** Still waiting for the user: what its unit word means. */
    val unknownUnit: Boolean get() = unitWord != null && unit == null

    /** Needs the user to look at it: an unknown unit word, or no pantry match. */
    val toCheck: Boolean get() = unknownUnit || match == LineMatch.None
}

/** Why a link gave no recipe to review. */
enum class ImportProblem { NotALink, NoRecipe, Failed }

data class ImportRecipeState(
    val today: LocalDate,
    val link: String = "",
    val reading: Boolean = false,
    val problem: ImportProblem? = null,
    val name: String = "",
    val servings: Int = DEFAULT_SERVINGS,
    /** The recipe's lines once read; empty while the link is being entered. */
    val lines: List<ImportLine> = emptyList(),
    /** The pantry, soonest expiry first, to match lines to. */
    val pantry: List<PantryItem> = emptyList(),
    /** Set once the meal is saved: the screen closes. */
    val done: Boolean = false,
) {
    val reviewing: Boolean get() = lines.isNotEmpty()

    val toCheck: Int get() = lines.count { it.toCheck }
}

@HiltViewModel
class ImportRecipeViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val meals: MealRepository,
    private val reader: RecipeReader,
    private val rulesStore: ImportRulesStore,
    private val clock: Clock,
) : ViewModel() {
    private val form = MutableStateFlow(ImportRecipeState(today = LocalDate.now(clock)))
    private var started = false
    private var rules = ImportRules()

    val state: StateFlow<ImportRecipeState> = combine(form, pantry.items()) { f, items ->
        f.copy(pantry = items.sortedWith(PantryItem.BySoonestExpiry))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    /** Fills in a shared [link]. Only the first call counts (the screen calls it on entry); nothing is fetched yet. */
    fun start(link: String) {
        if (started) return
        started = true
        form.update { it.copy(link = link) }
    }

    fun onLink(link: String) = form.update { it.copy(link = link, problem = null) }

    /** Fetches the link's page, once, and reads its recipe (`docs/adr/0003`). */
    fun onRead() {
        val f = form.value
        if (f.reading || f.link.isBlank()) return
        form.update { it.copy(reading = true, problem = null) }
        viewModelScope.launch {
            val result = reader.read(f.link)
            rules = rulesStore.rules().first()
            val items = pantry.items().first()
            val today = LocalDate.now(clock)
            form.update { s ->
                when (result) {
                    is RecipeResult.Found -> s.copy(
                        reading = false,
                        name = result.recipe.name,
                        servings = result.recipe.servings ?: DEFAULT_SERVINGS,
                        lines = result.recipe.ingredients.map { line(it, items, today) },
                    )
                    RecipeResult.NoRecipe -> s.copy(reading = false, problem = ImportProblem.NoRecipe)
                    RecipeResult.NotALink -> s.copy(reading = false, problem = ImportProblem.NotALink)
                    RecipeResult.Failed -> s.copy(reading = false, problem = ImportProblem.Failed)
                }
            }
        }
    }

    private fun line(text: String, items: List<PantryItem>, today: LocalDate): ImportLine {
        val parsed = parseIngredientLine(text, rules)
        val match = suggestMatch(parsed.name, items, rules, today)
        return ImportLine(
            text = parsed.text,
            amountText = parsed.amountText.orEmpty(),
            unitWord = parsed.unitWord,
            unit = parsed.unit?.takeIf { parsed.amount != null },
            name = parsed.name,
            match = match,
            contents = parsed.contents,
            askedUnit = parsed.unknownUnit,
            decided = match.learned(),
        )
    }

    private fun LineMatch.learned() = (this as? LineMatch.Item)?.learned == true || (this as? LineMatch.NotTracked)?.learned == true

    private fun edit(index: Int, change: (ImportLine) -> ImportLine) =
        form.update { s -> s.copy(lines = s.lines.mapIndexed { i, l -> if (i == index) change(l) else l }) }

    fun onName(name: String) = form.update { it.copy(name = name) }

    fun onServings(delta: Int) = form.update { it.copy(servings = (it.servings + delta).coerceIn(1, MAX_SERVINGS)) }

    fun onAmount(index: Int, text: String) = edit(index) { it.copy(amountText = text) }

    /** A line's name; a match the user hasn't chosen follows it. */
    fun onLineName(index: Int, name: String) {
        val s = state.value
        edit(index) { l -> l.copy(name = name, match = if (l.decided) l.match else suggestMatch(name, s.pantry, rules, s.today)) }
    }

    /**
     * A line's unit. Answering an unknown unit word is learned ("clove" is each) and fills in the other lines
     * with that word; changing a unit the recipe named drops its word, which no longer describes it.
     */
    fun onUnit(index: Int, unit: LineUnit) {
        val line = form.value.lines.getOrNull(index) ?: return
        val word = line.unitWord
        if (line.askedUnit && word != null) {
            rules = rules.withUnit(word, unit)
            val key = ImportRules.unitKey(word)
            form.update { s ->
                s.copy(lines = s.lines.mapIndexed { i, l -> if (i == index || (l.unknownUnit && l.unitWord?.let(ImportRules::unitKey) == key)) l.copy(unit = unit) else l })
            }
            viewModelScope.launch { rulesStore.learnUnit(word, unit) }
        } else {
            edit(index) { it.copy(unit = unit, unitWord = it.unitWord.takeIf { _ -> it.unit == unit }) }
        }
    }

    /** The line is [item]; learned for this name, and applied to other undecided lines with it. */
    fun onMatch(index: Int, item: PantryItem) = decide(index, LineMatch.Item(item), NameRule.Item(item.name))

    /** The line isn't tracked in the pantry; learned for this name. */
    fun onNotTracked(index: Int) = decide(index, LineMatch.NotTracked(), NameRule.NotTracked)

    private fun decide(index: Int, match: LineMatch, rule: NameRule) {
        val name = form.value.lines.getOrNull(index)?.name?.takeIf(String::isNotBlank) ?: return
        rules = rules.withName(name, rule)
        val key = ImportRules.nameKey(name)
        form.update { s ->
            s.copy(
                lines = s.lines.mapIndexed { i, l ->
                    if (i == index || (!l.decided && ImportRules.nameKey(l.name) == key)) l.copy(match = match, decided = true) else l
                },
            )
        }
        viewModelScope.launch { rulesStore.learnName(name, rule) }
    }

    /** Saves the meal through the same path as Plan a meal; lines left unmatched are not tracked. */
    fun onSave() {
        val s = form.value
        if (s.name.isBlank() || !s.reviewing) return
        val ingredients = s.lines.filter { it.name.isNotBlank() }.map { l ->
            importedIngredient(l.name, l.amountText.takeIf(String::isNotBlank), l.unitWord, l.unit, l.contents, l.match)
        }
        viewModelScope.launch {
            meals.save(Meal(s.name.trim(), s.servings, ingredients))
            form.update { it.copy(done = true) }
        }
    }

    private companion object {
        const val MAX_SERVINGS = 99
    }
}
