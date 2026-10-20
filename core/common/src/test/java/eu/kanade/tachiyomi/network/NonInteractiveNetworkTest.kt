package eu.kanade.tachiyomi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class NonInteractiveNetworkTest {

    private val seen = CopyOnWriteArrayList<Boolean>()

    // Answers without a network and records what an interceptor like Cloudflare's would see
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            seen += NonInteractiveNetwork.isNonInteractive(chain.call())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody())
                .build()
        }
        .build()

    private fun call() = client.newCall(Request.Builder().url("https://example.org/chapter").build())

    @Test
    fun `calls awaited under the context are non-interactive on OkHttp's threads`() {
        runBlocking(NonInteractiveNetwork) { call().await().close() }
        runBlocking { call().await().close() }

        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun `calls executed under the context are non-interactive, also after a dispatcher switch`() {
        runBlocking(NonInteractiveNetwork) {
            withContext(Dispatchers.IO) { call().execute().close() }
        }
        runBlocking(Dispatchers.IO) { call().execute().close() }

        assertEquals(listOf(true, false), seen)
    }
}
