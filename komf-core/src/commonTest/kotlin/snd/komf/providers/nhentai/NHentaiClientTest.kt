package snd.komf.providers.nhentai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

class NHentaiClientTest {

    @Test
    fun searchUsesPublicEndpointAndDeserializesResponse() = runBlocking {
        var capturedUrl: Url? = null
        val ktor = jsonMockClient {
            capturedUrl = it.url
            searchResponseJson
        }

        val results = NHentaiClient(ktor).search("sample query", limit = 5)

        val url = assertNotNull(capturedUrl)
        assertEquals("nhentai.net", url.host)
        assertEquals("/api/galleries/search", url.encodedPath)
        assertEquals("sample query", url.parameters["query"])
        assertEquals(1, results.size)
        assertEquals(123456L, results.single().id)
        assertEquals("Pretty Title", results.single().title.pretty)
        assertEquals("artist name", results.single().tags.single().name)
    }

    @Test
    fun getGalleryUsesPublicEndpointAndDeserializesResponse() = runBlocking {
        var capturedUrl: Url? = null
        val ktor = jsonMockClient {
            capturedUrl = it.url
            galleryResponseJson
        }

        val gallery = NHentaiClient(ktor).getGallery(123456)

        val url = assertNotNull(capturedUrl)
        assertEquals("nhentai.net", url.host)
        assertEquals("/api/gallery/123456", url.encodedPath)
        assertEquals(123456L, gallery.id)
        assertEquals("987654", gallery.mediaId)
        assertEquals("English Title", gallery.title.english)
        assertEquals(24, gallery.numPages)
    }

    @Test
    fun httpFailureKeepsResponseExceptionForOperatorLogs() = runBlocking {
        val ktor = HttpClient(MockEngine) {
            expectSuccess = true
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel("cloudflare challenge"),
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val exception = assertFailsWith<ResponseException> {
            NHentaiClient(ktor).search("sample", limit = 1)
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, exception.response.status)
    }

    private fun jsonMockClient(response: (io.ktor.client.request.HttpRequestData) -> String): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = ByteReadChannel(response(request)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private companion object {
        val galleryResponseJson = """
            {
              "id": 123456,
              "media_id": "987654",
              "title": {
                "english": "English Title",
                "japanese": "Japanese Title",
                "pretty": "Pretty Title"
              },
              "images": {
                "cover": { "t": "j", "w": 350, "h": 500 },
                "thumbnail": { "t": "p", "w": 250, "h": 350 }
              },
              "upload_date": 1700000000,
              "tags": [
                { "id": 1, "type": "artist", "name": "artist name", "url": "/artist/artist-name/", "count": 1 }
              ],
              "num_pages": 24
            }
        """.trimIndent()

        val searchResponseJson = """
            {
              "result": [$galleryResponseJson],
              "num_pages": 1,
              "per_page": 25
            }
        """.trimIndent()
    }
}
