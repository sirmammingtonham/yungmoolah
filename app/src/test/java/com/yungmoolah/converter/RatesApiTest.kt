package com.yungmoolah.converter

import com.yungmoolah.converter.data.RatesApi
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatesApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: RatesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RatesApi(baseUrl = server.url("/v6/latest/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `parses a successful response`() = runTest {
        server.enqueue(MockResponse().setBody(SUCCESS_BODY))

        val snapshot = api.fetchLatest("USD")

        assertEquals("USD", snapshot.baseCode)
        assertEquals(1.0, snapshot.rates["USD"]!!, 1e-9)
        assertEquals(0.9142, snapshot.rates["EUR"]!!, 1e-9)
        assertEquals(1_788_307_351_000L, snapshot.ratesUpdatedAtMillis)
        assertEquals(1_788_393_781_000L, snapshot.nextUpdateAtMillis)
        assertTrue(snapshot.fetchedAtMillis > 0L)
        assertEquals("/v6/latest/USD", server.takeRequest().path)
    }

    @Test
    fun `ignores unknown fields and drops unusable rates`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"result":"success","base_code":"USD","surprise_field":123,
                 "time_last_update_unix":1,"time_next_update_unix":2,
                 "rates":{"USD":1,"EUR":0.9,"BAD":0,"WORSE":-3}}
                """.trimIndent()
            )
        )

        val snapshot = api.fetchLatest("USD")

        assertEquals(setOf("USD", "EUR"), snapshot.rates.keys)
    }

    @Test
    fun `an HTTP error surfaces as IOException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertThrowsIo { api.fetchLatest("USD") }
    }

    @Test
    fun `malformed JSON surfaces as IOException`() = runTest {
        server.enqueue(MockResponse().setBody("not json at all"))
        assertThrowsIo { api.fetchLatest("USD") }
    }

    @Test
    fun `a provider-level error surfaces as IOException`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"result":"error","error-type":"unsupported-code"}""")
        )
        assertThrowsIo { api.fetchLatest("USD") }
    }

    @Test
    fun `an empty rate table surfaces as IOException`() = runTest {
        server.enqueue(MockResponse().setBody("""{"result":"success","base_code":"USD","rates":{}}"""))
        assertThrowsIo { api.fetchLatest("USD") }
    }

    @Test
    fun `a response missing its own base surfaces as IOException`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"result":"success","base_code":"USD","rates":{"EUR":0.9}}""")
        )
        assertThrowsIo { api.fetchLatest("USD") }
    }

    private inline fun assertThrowsIo(block: () -> Unit) {
        try {
            block()
        } catch (expected: IOException) {
            return
        }
        throw AssertionError("Expected an IOException so callers fall back to the cache")
    }

    private companion object {
        val SUCCESS_BODY = """
            {"result":"success","provider":"https://www.exchangerate-api.com",
             "time_last_update_unix":1788307351,"time_next_update_unix":1788393781,
             "base_code":"USD","rates":{"USD":1,"EUR":0.9142,"GBP":0.7891,"JPY":147.2}}
        """.trimIndent()
    }
}
