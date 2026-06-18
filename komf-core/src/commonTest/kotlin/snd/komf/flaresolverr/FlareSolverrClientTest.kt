package snd.komf.flaresolverr

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlareSolverrClientTest {

    @Test
    fun requestGetCallsV1EndpointAndDecodesSolution() = runBlocking {
        var requestBody = ""
        val ktor = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestBody = (request.body as TextContent).text
                    respond(
                        content = ByteReadChannel(responseJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        val client = KtorFlareSolverrClient(
            ktor = ktor,
            config = FlareSolverrConfig(
                enabled = true,
                url = "http://flaresolverr:8191",
                maxTimeout = 12_345,
                session = "komf",
            )
        )

        val solution = client.requestGet(
            url = "https://nhentai.net/api/gallery/123456",
            headers = mapOf(HttpHeaders.UserAgent to "custom-agent"),
            cookies = mapOf("igneous" to "cookie-value"),
        )

        assertTrue(requestBody.contains("\"cmd\":\"request.get\""))
        assertTrue(requestBody.contains("\"url\":\"https://nhentai.net/api/gallery/123456\""))
        assertTrue(requestBody.contains("\"maxTimeout\":12345"))
        assertTrue(requestBody.contains("\"session\":\"komf\""))
        assertTrue(requestBody.contains("\"User-Agent\":\"custom-agent\""))
        assertTrue(requestBody.contains("\"name\":\"igneous\""))
        assertEquals("solved body", solution?.response)
        assertEquals("cf_clearance", solution?.cookies?.single()?.name)
        assertEquals("clearance-value", solution?.cookies?.single()?.value)
    }

    private companion object {
        const val responseJson = """
            {
              "status": "ok",
              "message": "done",
              "solution": {
                "url": "https://nhentai.net/api/gallery/123456",
                "status": 200,
                "response": "solved body",
                "cookies": [
                  { "name": "cf_clearance", "value": "clearance-value" }
                ],
                "userAgent": "Mozilla/5.0"
              }
            }
        """
    }
}
