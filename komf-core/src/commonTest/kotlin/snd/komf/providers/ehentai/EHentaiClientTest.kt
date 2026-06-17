package snd.komf.providers.ehentai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.ResponseException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class EHentaiClientTest {

    @Test
    fun searchSendsConfiguredCookiesOnlyToEHentaiMetadataHost() = runBlocking {
        var host: String? = null
        var cookieHeader = ""
        var userAgent: String? = null
        val ktor = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    host = request.url.host
                    cookieHeader = request.headers.getAll(HttpHeaders.Cookie).orEmpty().joinToString("; ")
                    userAgent = request.headers[HttpHeaders.UserAgent]
                    respond(
                        content = ByteReadChannel("<html></html>"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        EHentaiClient(
            ktor = ktor,
            useExhentai = false,
            cookies = mapOf(
                "ipb_member_id" to "member",
                "ipb_pass_hash" to "hash",
            ),
            userAgent = "custom-agent",
        ).search("sample", limit = 5)

        assertEquals("e-hentai.org", host)
        assertTrue(cookieHeader.contains("ipb_member_id=member"))
        assertTrue(cookieHeader.contains("ipb_pass_hash=hash"))
        assertEquals("custom-agent", userAgent)
    }

    @Test
    fun searchParsesCookieHeaderAndMapCookiesTakePrecedence() = runBlocking {
        var cookieHeader = ""
        val ktor = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    cookieHeader = request.headers.getAll(HttpHeaders.Cookie).orEmpty().joinToString("; ")
                    respond(
                        content = ByteReadChannel("<html></html>"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                    )
                }
            }
        }

        EHentaiClient(
            ktor = ktor,
            useExhentai = true,
            cookieHeader = "ipb_member_id=from-header; ipb_pass_hash=from-header; igneous=from-header",
            cookies = mapOf(
                "ipb_member_id" to "from-map",
                "ipb_pass_hash" to "from-map",
            ),
        ).search("sample", limit = 5)

        assertTrue(cookieHeader.contains("ipb_member_id=from-map"))
        assertTrue(cookieHeader.contains("ipb_pass_hash=from-map"))
        assertTrue(cookieHeader.contains("igneous=from-header"))
        assertFalse(cookieHeader.contains("ipb_member_id=from-header"))
        assertFalse(cookieHeader.contains("ipb_pass_hash=from-header"))
    }

    @Test
    fun getGalleryPostsGdataRequestWithCookiesToApiHost() = runBlocking {
        var host: String? = null
        var cookieHeader = ""
        var requestBody = ""
        val ktor = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    host = request.url.host
                    cookieHeader = request.headers.getAll(HttpHeaders.Cookie).orEmpty().joinToString("; ")
                    requestBody = (request.body as TextContent).text
                    respond(
                        content = ByteReadChannel(gdataResponseJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val gallery = EHentaiClient(
            ktor = ktor,
            useExhentai = true,
            cookieHeader = "ipb_member_id=member; ipb_pass_hash=hash; igneous=igneous",
        ).getGallery(EHentaiGalleryId(gid = 123456, token = "tokenabc"))

        assertEquals("api.e-hentai.org", host)
        assertTrue(cookieHeader.contains("ipb_member_id=member"))
        assertTrue(cookieHeader.contains("ipb_pass_hash=hash"))
        assertTrue(cookieHeader.contains("igneous=igneous"))
        assertTrue(requestBody.contains("\"method\":\"gdata\""))
        assertTrue(requestBody.contains("123456"))
        assertTrue(requestBody.contains("tokenabc"))
        assertEquals("Gallery Title", gallery.title)
    }

    @Test
    fun imageRequestDoesNotSendConfiguredCookiesToCdnHost() = runBlocking {
        var host: String? = null
        var cookieHeader: String? = null
        var userAgent: String? = null
        val ktor = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    host = request.url.host
                    cookieHeader = request.headers.getAll(HttpHeaders.Cookie)?.joinToString("; ")
                    userAgent = request.headers[HttpHeaders.UserAgent]
                    respond(
                        content = ByteReadChannel("image-bytes"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Image.JPEG.toString()),
                    )
                }
            }
        }

        val image = EHentaiClient(
            ktor = ktor,
            useExhentai = true,
            cookies = mapOf(
                "ipb_member_id" to "member",
                "ipb_pass_hash" to "hash",
            ),
            userAgent = "custom-agent",
        ).getImage("https://cdn.example.test/thumb.jpg")

        assertEquals("cdn.example.test", host)
        assertEquals("image-bytes", image.bytes.decodeToString())
        assertFalse(cookieHeader.orEmpty().contains("ipb_member_id"))
        assertFalse(cookieHeader.orEmpty().contains("ipb_pass_hash"))
        assertEquals("custom-agent", userAgent)
    }

    @Test
    fun httpFailureKeepsResponseExceptionForOperatorLogs() = runBlocking {
        val ktor = HttpClient(MockEngine) {
            expectSuccess = true
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel("Forbidden"),
                        status = HttpStatusCode.Forbidden,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                    )
                }
            }
        }

        val exception = assertFailsWith<ResponseException> {
            EHentaiClient(
                ktor = ktor,
                useExhentai = true,
                cookies = mapOf("ipb_member_id" to "member"),
            ).getImage("https://exhentai.org/image.jpg")
        }

        assertEquals(HttpStatusCode.Forbidden, exception.response.status)
    }

    private companion object {
        val gdataResponseJson = """
            {
              "gmetadata": [
                {
                  "gid": 123456,
                  "token": "tokenabc",
                  "title": "Gallery Title",
                  "title_jpn": "Japanese Gallery Title",
                  "category": "Manga",
                  "thumb": "https://ehgt.org/thumb.jpg",
                  "uploader": "uploader",
                  "posted": "1700000000",
                  "rating": "4.5",
                  "tags": ["artist:artist name", "language:english"]
                }
              ]
            }
        """.trimIndent()
    }
}
