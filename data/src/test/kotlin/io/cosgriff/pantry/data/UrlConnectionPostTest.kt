package io.cosgriff.pantry.data

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [UrlConnectionPost] against a server on the test machine's loopback address, the JDK's own: what goes out on the
 * wire, and what is read back. Nothing reaches Open Food Facts.
 */
class UrlConnectionPostTest {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private var seen = mutableMapOf<String, String>()

    @After
    fun stop() = server.stop(0)

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @Test
    fun `posts the form with the agent and cookie, and reads the answer and its cookies`() {
        server.createContext("/cgi/session.pl") { exchange ->
            seen["method"] = exchange.requestMethod
            seen["agent"] = exchange.requestHeaders.getFirst("User-Agent")
            seen["cookie"] = exchange.requestHeaders.getFirst("Cookie")
            seen["type"] = exchange.requestHeaders.getFirst("Content-Type")
            seen["body"] = exchange.requestBody.bufferedReader().readText()
            exchange.responseHeaders.add("Set-Cookie", "session=abc; path=/")
            val body = """{"user_id":"test-user"}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val reply = UrlConnectionPost().post(url("/cgi/session.pl"), "Pantry/1.0 (test)", mapOf("user_id" to "test user", "password" to "a&b=c"), "session=old")
        assertEquals(HttpReply(200, """{"user_id":"test-user"}""", listOf("session=abc; path=/")), reply)
        assertEquals("POST", seen["method"])
        assertEquals("Pantry/1.0 (test)", seen["agent"])
        assertEquals("session=old", seen["cookie"])
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", seen["type"])
        assertEquals("user_id=test+user&password=a%26b%3Dc", seen["body"])
    }

    @Test
    fun `a redirect is not followed, and an error's body is still read`() {
        server.createContext("/moved") { exchange ->
            exchange.requestBody.readBytes()
            exchange.responseHeaders.add("Location", "/elsewhere")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/busy") { exchange ->
            exchange.requestBody.readBytes()
            val body = "busy".toByteArray()
            exchange.sendResponseHeaders(503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        assertEquals(302, UrlConnectionPost().post(url("/moved"), "Pantry/1.0 (test)", emptyMap(), null)?.code)
        assertEquals(HttpReply(503, "busy"), UrlConnectionPost().post(url("/busy"), "Pantry/1.0 (test)", emptyMap(), null))
    }

    @Test
    fun `no server is null, not an exception`() {
        server.start()
        val port = server.address.port
        server.stop(0)
        assertNull(UrlConnectionPost(timeoutMillis = 1_000).post("http://127.0.0.1:$port/x", "Pantry/1.0 (test)", emptyMap(), null))
    }
}
