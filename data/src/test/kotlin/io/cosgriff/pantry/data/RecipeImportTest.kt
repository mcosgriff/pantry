package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.domain.ImportRules
import io.cosgriff.pantry.domain.LineUnit
import io.cosgriff.pantry.domain.MeasureUnit
import io.cosgriff.pantry.domain.NameRule
import io.cosgriff.pantry.domain.Package
import io.cosgriff.pantry.domain.RecipePage
import io.cosgriff.pantry.domain.RecipeResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs on Robolectric for the platform's `org.json`. The pages in `src/test/resources/recipes` are small
 * hand-written samples in the shapes recipe sites use, not copies of real pages; no request leaves the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecipeImportTest {
    private val requests = mutableListOf<Pair<String, String>>()

    private fun page(name: String) = javaClass.getResource("/recipes/$name.html")!!.readText()

    private fun reader(code: Int = 200, body: String = page("graph")) =
        JsonLdRecipeReader({ url, agent -> requests += url to agent; code to body }, userAgent = "Pantry/1.0 (test)")

    @Test
    fun `fetches only the link it was given, once, naming the app`() = runBlocking {
        reader().read(" https://recipes.example/soup ")
        assertEquals(listOf("https://recipes.example/soup" to "Pantry/1.0 (test)"), requests)
    }

    @Test
    fun `reads a Recipe inside a graph`() = runBlocking {
        assertEquals(
            RecipeResult.Found(
                RecipePage(
                    "Sample black bean soup & lime",
                    4,
                    listOf("1 (15 oz) can black beans, drained", "2 cups chicken stock", "½ tsp salt", "2 cloves garlic, minced"),
                ),
            ),
            reader().read("https://recipes.example/soup"),
        )
    }

    @Test
    fun `reads a Recipe in an array, typed with others, with a number for its yield`() = runBlocking {
        assertEquals(
            RecipeResult.Found(RecipePage("Sample pancakes", 6, listOf("2 1/2 cups all-purpose flour", "3 large eggs", "1 cup milk"))),
            reader(body = page("array")).read("https://recipes.example/pancakes"),
        )
    }

    @Test
    fun `a page without Recipe data says so, and isn't guessed from`() = runBlocking {
        assertEquals(RecipeResult.NoRecipe, reader(body = page("none")).read("https://recipes.example/tips"))
        assertEquals(RecipeResult.NoRecipe, reader(body = "<html></html>").read("https://recipes.example/tips"))
    }

    @Test
    fun `anything but an https link is refused before any request`() = runBlocking {
        listOf("http://recipes.example/soup", "file:///sdcard/soup.html", "just some words", "").forEach {
            assertEquals(it, RecipeResult.NotALink, reader().read(it))
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `errors and no connection fail quietly`() = runBlocking {
        assertEquals(RecipeResult.Failed, reader(code = 404).read("https://recipes.example/soup"))
        assertEquals(RecipeResult.Failed, JsonLdRecipeReader({ _, _ -> null }, "Pantry/1.0 (test)").read("https://recipes.example/soup"))
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "rules_test.preferences_pb").apply { delete() }
    private val rules = DataStoreImportRules(PreferenceDataStoreFactory.create(scope = scope) { file })

    @After
    fun close() = scope.cancel()

    @Test
    fun `learned rules are kept and read back`() = runBlocking {
        assertEquals(ImportRules(), rules.rules().first())
        rules.learnUnit("Cloves", LineUnit.Of(MeasureUnit.Each))
        rules.learnUnit("sleeve", LineUnit.In(Package.Pack))
        rules.learnName("Black Beans", NameRule.Item("Sample black beans"))
        rules.learnName("salt and pepper", NameRule.NotTracked)
        assertEquals(
            ImportRules()
                .withUnit("clove", LineUnit.Of(MeasureUnit.Each))
                .withUnit("sleeve", LineUnit.In(Package.Pack))
                .withName("black bean", NameRule.Item("Sample black beans"))
                .withName("salt and pepper", NameRule.NotTracked),
            rules.rules().first(),
        )
    }

    @Test
    fun `a rule the app no longer understands is skipped`() {
        val stored = """{"units":{"clove":"unit:Each","sprig":"unit:Handful"},"names":{"egg":"item:Eggs","x":"maybe"}}"""
        assertEquals(
            ImportRules().withUnit("clove", LineUnit.Of(MeasureUnit.Each)).withName("egg", NameRule.Item("Eggs")),
            DataStoreImportRules.decode(stored),
        )
        assertEquals(ImportRules(), DataStoreImportRules.decode("not json"))
    }
}
