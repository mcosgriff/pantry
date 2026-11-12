package io.cosgriff.pantry.data

import io.cosgriff.pantry.domain.NewProduct
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** An HTTP answer: the status code, the body and any `Set-Cookie` headers. */
data class HttpReply(val code: Int, val body: String, val cookies: List<String> = emptyList())

/** One HTTP POST of a form, with a `Cookie` header when [cookie] is set; `null` if the request didn't complete. */
fun interface HttpPost {
    fun post(url: String, userAgent: String, form: Map<String, String>, cookie: String?): HttpReply?
}

/** How signing in to Open Food Facts went. */
sealed interface SignInResult {
    /** [user] is the account's user name as Open Food Facts has it; [session] the cookie to send with writes. */
    data class SignedIn(val user: String, val session: String) : SignInResult

    /** Open Food Facts didn't accept the user name and password. */
    data object WrongDetails : SignInResult

    /** Offline, slow or an error: try again. */
    data object Failed : SignInResult
}

/** How sharing a product went. */
enum class ShareResult {
    Shared,

    /** Someone added it meanwhile: nothing was sent, so nothing of theirs was replaced. */
    AlreadyThere,

    /** No session, or Open Food Facts no longer accepts it: sign in again. */
    SignedOut,

    /** Open Food Facts said no to the fields, or there was nothing it could take. */
    Rejected,

    /** Offline, slow or an error: try again. */
    Failed,
}

/** Adds products to Open Food Facts with the user's own account (#42, `docs/adr/0004`). */
interface ProductWriter {
    suspend fun signIn(user: String, password: String): SignInResult

    suspend fun add(product: NewProduct, session: String): ShareResult
}

/**
 * Open Food Facts' write API, as decided in `docs/adr/0004`. Signing in posts the user name and password once to
 * `/cgi/session.pl` and keeps only the session cookie it answers with; adding posts [NewProduct.fields] (the
 * least Open Food Facts takes) to `/cgi/product_jqm2.pl` with that cookie, plus the app's name and version, as
 * Open Food Facts asks of apps. Any failure is a result, never an exception.
 */
class OpenFoodFactsWriter(
    private val http: HttpPost,
    private val userAgent: String,
    private val appVersion: String,
    private val baseUrl: String = "https://world.openfoodfacts.org",
) : ProductWriter {
    override suspend fun signIn(user: String, password: String): SignInResult {
        val name = user.trim()
        if (name.isEmpty() || password.isEmpty()) return SignInResult.WrongDetails
        // jqm=1 makes session.pl answer in JSON: the user name when signed in, an error otherwise.
        val form = mapOf("user_id" to name, "password" to password, "jqm" to "1")
        val reply = withContext(Dispatchers.IO) { http.post("$baseUrl/cgi/session.pl", userAgent, form, cookie = null) }
            ?: return SignInResult.Failed
        return readSignIn(reply)
    }

    override suspend fun add(product: NewProduct, session: String): ShareResult {
        val fields = product.fields() ?: return ShareResult.Rejected
        val form = fields + mapOf("app_name" to APP_NAME, "app_version" to appVersion)
        val reply = withContext(Dispatchers.IO) { http.post("$baseUrl/cgi/product_jqm2.pl", userAgent, form, session) }
            ?: return ShareResult.Failed
        return readAdd(reply)
    }

    companion object {
        const val APP_NAME = "Pantry"

        /** Signed in when the answer names the user and sets a non-empty `session` cookie. */
        fun readSignIn(reply: HttpReply): SignInResult {
            if (reply.code == HttpURLConnection.HTTP_UNAUTHORIZED || reply.code == HttpURLConnection.HTTP_FORBIDDEN) {
                return SignInResult.WrongDetails
            }
            if (reply.code != HttpURLConnection.HTTP_OK) return SignInResult.Failed
            val user = try {
                JSONObject(reply.body).optString("user_id").takeIf { it.isNotBlank() }
            } catch (_: JSONException) {
                return SignInResult.Failed
            }
            val session = sessionCookie(reply.cookies)
            return if (user != null && session != null) SignInResult.SignedIn(user, session) else SignInResult.WrongDetails
        }

        /** `status` 1 is saved; "no user credentials" means the session has ended. */
        fun readAdd(reply: HttpReply): ShareResult {
            if (reply.code == HttpURLConnection.HTTP_UNAUTHORIZED || reply.code == HttpURLConnection.HTTP_FORBIDDEN) {
                return ShareResult.SignedOut
            }
            if (reply.code != HttpURLConnection.HTTP_OK) return ShareResult.Failed
            return try {
                val json = JSONObject(reply.body)
                when {
                    json.optInt("status") == 1 -> ShareResult.Shared
                    json.optString("status_verbose") == "no user credentials" -> ShareResult.SignedOut
                    else -> ShareResult.Rejected
                }
            } catch (_: JSONException) {
                ShareResult.Failed
            }
        }

        /** `session=<value>` from the `Set-Cookie` headers, without its attributes; `null` if absent or empty. */
        fun sessionCookie(setCookies: List<String>): String? = setCookies
            .map { it.substringBefore(';').trim() }
            .lastOrNull { it.startsWith("session=") && it.length > "session=".length }
    }
}

/**
 * [HttpPost] over the platform's `HttpURLConnection`, as the lookup's [UrlConnectionGet]: no HTTP library, no
 * cookie jar (the one cookie is passed in by hand) and no redirects followed.
 */
class UrlConnectionPost(private val timeoutMillis: Int = 15_000) : HttpPost {
    override fun post(url: String, userAgent: String, form: Map<String, String>, cookie: String?): HttpReply? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            cookie?.let { connection.setRequestProperty("Cookie", it) }
            val body = formBody(form).toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val cookies = connection.headerFields.filterKeys { it.equals("Set-Cookie", ignoreCase = true) }.values.flatten()
            HttpReply(code, text, cookies)
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        null
    }

    companion object {
        fun formBody(form: Map<String, String>): String =
            form.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
    }
}
