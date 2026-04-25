package com.charles.ollama.client.data.api

import com.charles.ollama.client.data.api.dto.ModelListResponse
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.runBlocking

/**
 * Contract test against a real `GET /api/tags` response captured from a
 * production Ollama server. Reproduces the exact deserialization path used by
 * `OllamaApiFactory` so a regression in either the DTO shape or the Retrofit
 * wiring fails here at unit-test time instead of at install time.
 *
 * R8/ProGuard strips generic signatures by default, which produces the
 * runtime error "java.lang.Class cannot be cast to
 * java.lang.reflect.ParameterizedType" when Gson tries to read
 * `List<ModelInfo>` off `ModelListResponse.models`. This test only exercises
 * the source-level contract — the proguard-rules.pro changes guard the
 * minified release variant separately.
 */
class OllamaApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OllamaApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val gson = GsonBuilder().setLenient().create()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OllamaApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listModels parses real Ollama tags response`() = runBlocking {
        val body = javaClass.classLoader!!
            .getResourceAsStream("fixtures/api_tags_response.json")!!
            .bufferedReader()
            .readText()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val response = api.listModels()
        assertTrue("HTTP failed: ${response.code()}", response.isSuccessful)
        val parsed: ModelListResponse? = response.body()
        assertNotNull("Response body deserialized to null", parsed)
        assertTrue("Expected at least one model in the fixture", parsed!!.models.isNotEmpty())

        // First entry in the captured fixture — sanity-check key fields so a
        // silent DTO drift (e.g. renaming a @SerializedName) shows up here.
        val first = parsed.models.first()
        assertEquals("gemma4:e2b", first.name)
        assertNotNull(first.details)
        assertEquals("gguf", first.details!!.format)

        // The fixture also contains "cloud" entries with `families: null` and
        // tiny sizes — proves Gson tolerates the optional/nullable fields the
        // real Ollama server emits in its bookkeeping for remote models.
        val withNullFamilies = parsed.models.firstOrNull { it.details?.families == null }
        assertNotNull("Fixture should include an entry with null families", withNullFamilies)
    }
}
