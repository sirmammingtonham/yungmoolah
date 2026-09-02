package com.yungmoolah.converter.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** The provider's response envelope. Only the fields we actually use are declared. */
@Serializable
private data class RatesResponse(
    val result: String = "",
    val base_code: String = "",
    val time_last_update_unix: Long = 0L,
    val time_next_update_unix: Long = 0L,
    val rates: Map<String, Double> = emptyMap(),
)

/**
 * Fetches rates from open.er-api.com — the keyless tier of exchangerate-api.com,
 * which republishes once a day and needs no account or API key.
 */
class RatesApi(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    /** Injectable so tests can keep the blocking call on their own scheduler. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Downloads the newest rates for [base].
     *
     * @throws IOException on any network, HTTP or payload problem, so callers only
     *   have one failure mode to handle when falling back to the cache.
     */
    suspend fun fetchLatest(base: String = DEFAULT_BASE): RatesSnapshot = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("$baseUrl$base")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Rates request failed with HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val parsed = try {
                json.decodeFromString<RatesResponse>(body)
            } catch (e: SerializationException) {
                throw IOException("Could not parse rates response", e)
            }

            if (parsed.result != "success") {
                throw IOException("Rates provider reported '${parsed.result.ifEmpty { "no result" }}'")
            }
            val rates = parsed.rates.filterValues { it > 0.0 && it.isFinite() }
            if (rates.isEmpty()) throw IOException("Rates response contained no usable rates")

            val baseCode = parsed.base_code.ifEmpty { base }
            if (!rates.containsKey(baseCode)) throw IOException("Rates response is missing its own base")

            RatesSnapshot(
                baseCode = baseCode,
                rates = rates,
                fetchedAtMillis = System.currentTimeMillis(),
                ratesUpdatedAtMillis = parsed.time_last_update_unix * 1_000L,
                nextUpdateAtMillis = parsed.time_next_update_unix * 1_000L,
            )
        }
    }

    companion object {
        const val DEFAULT_BASE = "USD"
        const val DEFAULT_BASE_URL = "https://open.er-api.com/v6/latest/"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
