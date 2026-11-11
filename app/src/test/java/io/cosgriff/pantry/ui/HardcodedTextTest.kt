package io.cosgriff.pantry.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Android lint's hard-coded-text check only looks at XML layouts, so this does it for the Compose code
 * (#5): no words typed straight into what the UI shows. Wording goes in `strings.xml`; text that is only
 * data, such as `"$count"`, is fine. Add a shared composable that takes text to [calls] when it is made.
 */
class HardcodedTextTest {
    private val calls = Regex("""\b(Text|AnnotatedString|buildAnnotatedString)\(\s*"((?:[^"\\]|\\.)*)"""")
    private val named = Regex(
        """\b(contentDescription|onClickLabel|onLongClickLabel|label|title|subtitle|text|placeholder|""" +
            """supportingText|actionLabel|message)\s*=\s*"((?:[^"\\]|\\.)*)"""",
    )

    @Test
    fun `no words are typed straight into the UI`() {
        val found = File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex().flatMap { (i, line) ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("import ")) emptyList()
                else (calls.findAll(code) + named.findAll(code))
                    .map { it.groupValues[2] }
                    .filter { words(it) }
                    .map { "${file.path}:${i + 1}: \"$it\"" }
                    .toList()
            }
        }.toList()
        assertEquals("Move these into strings.xml:\n" + found.joinToString("\n"), emptyList<String>(), found)
    }

    /** Letters left once the interpolated data is taken out. */
    private fun words(literal: String): Boolean =
        literal.replace(Regex("""\$\{[^}]*\}|\$\w+"""), "").any { it.isLetter() }
}
