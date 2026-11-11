package io.cosgriff.pantry.data

import io.cosgriff.pantry.domain.NewProduct
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs on Robolectric for the platform's `org.json`. Made-up accounts and products; no request leaves the test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenFoodFactsWriterTest {
    private data class Sent(val url: String, val agent: String, val form: Map<String, String>, val cookie: String?)

    private val sent = mutableListOf<Sent>()

    private fun writer(reply: HttpReply?) = OpenFoodFactsWriter(
        { url, agent, form, cookie -> sent += Sent(url, agent, form, cookie); reply },
        userAgent = "Pantry/1.0 (test)",
        appVersion = "1.0",
    )

    @Test
    fun `signing in posts the user name and password once and keeps only the session cookie`() = runBlocking {
        val reply = HttpReply(200, """{"user_id":"test-user","name":"Test"}""", listOf("session=test-user&abc123; domain=.openfoodfacts.org; path=/"))
        assertEquals(SignInResult.SignedIn("test-user", "session=test-user&abc123"), writer(reply).signIn(" test-user ", "test-password"))
        assertEquals(
            listOf(Sent("https://world.openfoodfacts.org/cgi/session.pl", "Pantry/1.0 (test)", mapOf("user_id" to "test-user", "password" to "test-password", "jqm" to "1"), null)),
            sent,
        )
    }

    @Test
    fun `a wrong password, no session or an error is not signed in`() = runBlocking {
        assertEquals(SignInResult.WrongDetails, writer(HttpReply(200, """{"error":"undefined user"}""")).signIn("test-user", "nope"))
        assertEquals(SignInResult.WrongDetails, writer(HttpReply(200, """{"user_id":"test-user"}""", listOf("session=; path=/"))).signIn("test-user", "nope"))
        assertEquals(SignInResult.WrongDetails, writer(HttpReply(403, "")).signIn("test-user", "nope"))
        assertEquals(SignInResult.Failed, writer(HttpReply(503, "busy")).signIn("test-user", "pw"))
        assertEquals(SignInResult.Failed, writer(HttpReply(200, "<html>")).signIn("test-user", "pw"))
        assertEquals(SignInResult.Failed, writer(null).signIn("test-user", "pw"))
    }

    @Test
    fun `an empty user name or password is never sent`() = runBlocking {
        assertEquals(SignInResult.WrongDetails, writer(null).signIn("  ", "pw"))
        assertEquals(SignInResult.WrongDetails, writer(null).signIn("test-user", ""))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `adding sends the product's fields, the app's name and version, and the session`() = runBlocking {
        val result = writer(HttpReply(200, """{"status":1,"status_verbose":"fields saved"}"""))
            .add(NewProduct("041220576913", "Test water", size = "12 fl oz"), "session=test")
        assertEquals(ShareResult.Shared, result)
        assertEquals(
            listOf(
                Sent(
                    "https://world.openfoodfacts.org/cgi/product_jqm2.pl",
                    "Pantry/1.0 (test)",
                    mapOf("code" to "041220576913", "product_name" to "Test water", "quantity" to "12 fl oz", "app_name" to "Pantry", "app_version" to "1.0"),
                    "session=test",
                ),
            ),
            sent,
        )
    }

    @Test
    fun `an ended session, a refusal and an error each say so`() = runBlocking {
        val product = NewProduct("041220576913", "Test water")
        assertEquals(ShareResult.SignedOut, writer(HttpReply(200, """{"status":0,"status_verbose":"no user credentials"}""")).add(product, "s"))
        assertEquals(ShareResult.SignedOut, writer(HttpReply(401, "")).add(product, "s"))
        assertEquals(ShareResult.Rejected, writer(HttpReply(200, """{"status":0,"status_verbose":"Edit against edit rules"}""")).add(product, "s"))
        assertEquals(ShareResult.Failed, writer(HttpReply(502, "")).add(product, "s"))
        assertEquals(ShareResult.Failed, writer(HttpReply(200, "<html>")).add(product, "s"))
        assertEquals(ShareResult.Failed, writer(null).add(product, "s"))
    }

    @Test
    fun `a store-packed label is never sent`() = runBlocking {
        assertEquals(ShareResult.Rejected, writer(null).add(NewProduct("204711018426", "Test steak"), "s"))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `the session cookie is read without its attributes`() {
        assertEquals("session=a&b", OpenFoodFactsWriter.sessionCookie(listOf("lang=en; path=/", "session=a&b; domain=.x; path=/")))
        assertNull(OpenFoodFactsWriter.sessionCookie(listOf("lang=en")))
    }
}
