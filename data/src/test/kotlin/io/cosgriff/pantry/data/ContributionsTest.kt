package io.cosgriff.pantry.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.pantry.domain.LookupResult
import io.cosgriff.pantry.domain.NewProduct
import io.cosgriff.pantry.domain.ProductInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fakes for Open Food Facts and the Keystore; made-up accounts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContributionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "contributions_test.preferences_pb").apply { delete() }
    private val store = PreferenceDataStoreFactory.create(scope = scope) { file }

    /** Reverses the text: stands in for the Keystore, which Robolectric doesn't have. */
    private val box = object : SecretBox {
        override fun seal(plain: String) = "sealed:" + plain.reversed()
        override fun open(sealed: String) = sealed.removePrefix("sealed:").reversed()
    }

    private val sessionsSent = mutableListOf<String>()
    private var signIn: SignInResult = SignInResult.SignedIn("test-user", "session=test")
    private var add = ShareResult.Shared
    private var lookup: LookupResult = LookupResult.NotFound

    private val writer = object : ProductWriter {
        override suspend fun signIn(user: String, password: String) = signIn
        override suspend fun add(product: NewProduct, session: String): ShareResult {
            sessionsSent += session
            return add
        }
    }
    private val contributions = Contributions(store, writer, { lookup }, box)
    private val water = NewProduct("041220576913", "Test water")

    @After
    fun close() = scope.cancel()

    @Test
    fun `signing in keeps the user name and the sealed session, never the password`() = runBlocking {
        assertNull(contributions.account().first())
        contributions.signIn("test-user", "test-password")
        assertEquals("test-user", contributions.account().first())
        val stored = store.data.first().asMap().values.map(Any::toString)
        assertTrue("sealed:tset=noisses" in stored)
        assertFalse(stored.any { "test-password" in it || it == "session=test" })
    }

    @Test
    fun `a failed sign-in stores nothing`() = runBlocking {
        signIn = SignInResult.WrongDetails
        assertEquals(SignInResult.WrongDetails, contributions.signIn("test-user", "nope"))
        assertNull(contributions.account().first())
    }

    @Test
    fun `sharing sends the session, counts the product, and needs signing in first`() = runBlocking {
        assertEquals(ShareResult.SignedOut, contributions.share(water))
        contributions.signIn("test-user", "pw")
        assertEquals(ShareResult.Shared, contributions.share(water))
        assertEquals(listOf("session=test"), sessionsSent)
        assertEquals(1, contributions.sharedCount().first())
    }

    @Test
    fun `a product someone added meanwhile is left alone, and an unreachable lookup sends nothing`() = runBlocking {
        contributions.signIn("test-user", "pw")
        lookup = LookupResult.Found(ProductInfo("Their water", null, null))
        assertEquals(ShareResult.AlreadyThere, contributions.share(water))
        lookup = LookupResult.Failed
        assertEquals(ShareResult.Failed, contributions.share(water))
        assertTrue(sessionsSent.isEmpty())
    }

    @Test
    fun `an ended session is forgotten, and signing out forgets it too`() = runBlocking {
        contributions.signIn("test-user", "pw")
        add = ShareResult.SignedOut
        assertEquals(ShareResult.SignedOut, contributions.share(water))
        assertNull(contributions.account().first())
        contributions.signIn("test-user", "pw")
        contributions.signOut()
        assertNull(contributions.account().first())
        assertTrue(store.data.first().asMap().isEmpty())
    }

    @Test
    fun `a product without a name is never sent`() = runBlocking {
        contributions.signIn("test-user", "pw")
        assertEquals(ShareResult.Rejected, contributions.share(NewProduct("041220576913", " ")))
        assertTrue(sessionsSent.isEmpty())
    }
}
