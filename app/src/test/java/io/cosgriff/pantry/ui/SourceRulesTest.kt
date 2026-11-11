package io.cosgriff.pantry.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules in CLAUDE.md that are a fixed pattern in the source, checked here so the build catches them
 * instead of review (#5). A rule that needs judgement stays with review. Paths are relative to `app/`.
 */
class SourceRulesTest {
    private class Rule(
        val why: String,
        val pattern: Regex,
        val roots: List<String>,
        val allowedIn: Set<String> = emptySet(),
        val extensions: Set<String> = setOf("kt"),
    )

    private val app = listOf("src/main/java")
    private val theme = "ui/theme/"

    private val rules = listOf(
        Rule(
            "Dynamic color is off: every color comes from the Pantry tokens",
            Regex("""\bdynamic(Light|Dark)ColorScheme\b"""), app,
        ),
        Rule(
            "Colors come from MaterialTheme.colorScheme or PantryTheme.colors, never a literal",
            Regex("""\bColor\(\s*0x"""), app, allowedIn = setOf("${theme}Color.kt"),
        ),
        Rule(
            "Spacing and sizes come from PantryDimens, never a literal dp",
            Regex("""\b\d+(\.\d+)?\.dp\b"""), app, allowedIn = setOf("${theme}Shape.kt"),
        ),
        Rule(
            "Corners come from MaterialTheme.shapes, never a new RoundedCornerShape",
            Regex("""\bRoundedCornerShape\("""), app, allowedIn = setOf("${theme}Shape.kt"),
        ),
        Rule(
            "Never format a figure or date for display with String.format",
            Regex("""\bString\.format\s*\(|"\s*\.format\s*\("""), app,
        ),
        Rule(
            "The app runs in the user's locale: Locale.US only to change case",
            Regex("""(?<!uppercase\(|lowercase\()\bLocale\.US\b"""), app,
        ),
        Rule(
            "Composables use stringResource, never LocalContext.current.getString",
            Regex("""\bLocalContext\.current\.getString\b"""), app,
        ),
        Rule(
            "A …Content composable takes today as a parameter; only a ViewModel or the expiry digest reads the clock",
            Regex("""\b(LocalDate|LocalDateTime|ZonedDateTime|Instant)\.now\("""), app,
            allowedIn = setOf("ViewModel.kt", "notify/ExpiryDigest.kt"),
        ),
        Rule(
            "Barcodes are read with zxing-cpp, never ML Kit: it uploads usage logs to Google",
            Regex("""com\.google\.mlkit|com\.google\.android\.gms:play-services-mlkit"""),
            listOf("src", "../gradle", "build.gradle.kts", "../domain"),
            extensions = setOf("kt", "kts", "toml"),
        ),
    )

    private fun Rule.files(): Sequence<File> = roots.asSequence().flatMap { File(it).walk() }
        .filter { it.isFile && it.extension in extensions }
        .filter { file -> allowedIn.none { file.invariantSeparatorsPath.endsWith(it) } }

    @Test
    fun `the source keeps the fixed-pattern rules`() {
        val found = rules.flatMap { rule ->
            rule.files().flatMap { file ->
                file.readLines().withIndex().mapNotNull { (i, line) ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("*") || code.startsWith("#") || !rule.pattern.containsMatchIn(code)) null
                    else "${file.path}:${i + 1}: ${rule.why}"
                }
            }
        }
        assertEquals(found.joinToString("\n"), emptyList<String>(), found)
    }

    @Test
    fun `every rule reads source that is there`() {
        rules.forEach { rule ->
            assertTrue("${rule.why}: no file to read in ${rule.roots}", rule.roots.any { File(it).exists() })
        }
        assertTrue("src/main/java has no Kotlin files", File("src/main/java").walk().any { it.extension == "kt" })
        listOf("Color.kt", "Shape.kt").forEach { assertTrue("$theme$it moved", File("src/main/java/io/cosgriff/pantry/$theme$it").exists()) }
    }
}
