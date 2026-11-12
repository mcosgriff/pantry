package io.cosgriff.pantry.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.cosgriff.pantry.domain.LookupResult
import io.cosgriff.pantry.domain.NewProduct
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Seals a secret for storing on the phone, and opens it again; `null` if it can't be opened. */
interface SecretBox {
    fun seal(plain: String): String

    fun open(sealed: String): String?
}

/**
 * [SecretBox] with an AES-GCM key kept in the Android Keystore, which never lets the key out of the phone's secure
 * hardware: platform APIs only, no library (`docs/adr/0004`). A key lost (the app's data cleared) opens nothing,
 * and the user signs in again.
 */
class KeystoreSecretBox(private val alias: String = "open_food_facts_session") : SecretBox {
    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val base64 = Base64.getEncoder()
        return base64.encodeToString(cipher.iv) + SEPARATOR + base64.encodeToString(sealed)
    }

    override fun open(sealed: String): String? = try {
        val (iv, data) = sealed.split(SEPARATOR).map(Base64.getDecoder()::decode).takeIf { it.size == 2 } ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val SEPARATOR = ":"
    }
}

/**
 * Contributing to Open Food Facts (#42, `docs/adr/0004`): the user's own account, signed in on the phone. The
 * password is sent once, to Open Food Facts, and never stored; the session cookie it answers with is kept here,
 * sealed by [box], and sent nowhere but Open Food Facts. The user name and a count of products shared are kept
 * beside it, for Pantry settings.
 */
@Singleton
class Contributions @Inject constructor(
    private val store: DataStore<Preferences>,
    private val writer: ProductWriter,
    private val lookup: ProductLookup,
    private val box: SecretBox,
) {
    /** The signed-in Open Food Facts user name, or `null`. */
    fun account(): Flow<String?> = store.data.map { prefs -> prefs[USER]?.takeIf { prefs[SESSION] != null } }

    /** Products shared from this phone, for Pantry settings. */
    fun sharedCount(): Flow<Int> = store.data.map { it[SHARED] ?: 0 }

    suspend fun signIn(user: String, password: String): SignInResult {
        val result = writer.signIn(user, password)
        if (result is SignInResult.SignedIn) {
            val sealed = withContext(Dispatchers.IO) { box.seal(result.session) }
            store.edit {
                it[USER] = result.user
                it[SESSION] = sealed
            }
        }
        return result
    }

    /** Forgets the session on the phone. Open Food Facts ends a user's older sessions by itself. */
    suspend fun signOut() {
        store.edit {
            it.remove(USER)
            it.remove(SESSION)
        }
    }

    /**
     * Shares [product], signed in. It is looked up first, so a product someone added meanwhile is left as it is
     * ([ShareResult.AlreadyThere]). A session Open Food Facts no longer accepts is forgotten.
     */
    suspend fun share(product: NewProduct): ShareResult {
        if (!product.shareable) return ShareResult.Rejected
        val session = session() ?: return ShareResult.SignedOut
        when (lookup.lookUp(product.barcode)) {
            is LookupResult.Found -> return ShareResult.AlreadyThere
            LookupResult.Failed -> return ShareResult.Failed
            LookupResult.NotFound -> Unit
        }
        val result = writer.add(product, session)
        when (result) {
            ShareResult.Shared -> store.edit { it[SHARED] = (it[SHARED] ?: 0) + 1 }
            ShareResult.SignedOut -> signOut()
            else -> Unit
        }
        return result
    }

    private suspend fun session(): String? {
        val sealed = store.data.first()[SESSION] ?: return null
        return withContext(Dispatchers.IO) { box.open(sealed) }
    }

    private companion object {
        val USER = stringPreferencesKey("off_user")
        val SESSION = stringPreferencesKey("off_session_sealed")
        val SHARED = intPreferencesKey("off_shared_count")
    }
}
