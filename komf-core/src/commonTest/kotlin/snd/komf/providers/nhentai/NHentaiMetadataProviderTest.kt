package snd.komf.providers.nhentai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import snd.komf.model.AuthorRole
import snd.komf.model.ProviderSeriesId
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.SeriesMetadataConfig
import snd.komf.util.NameSimilarityMatcher.Companion.nameSimilarityMatcher
import snd.komf.util.NameSimilarityMatcher.NameMatchingMode.EXACT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NHentaiMetadataProviderTest {

    @Test
    fun getSeriesMetadataAcceptsNhentaiUrlProviderId() = runBlocking {
        var capturedUrl: Url? = null
        val client = NHentaiClient(HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedUrl = request.url
                    respond(
                        content = ByteReadChannel(galleryResponseJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        })
        val provider = NHentaiMetadataProvider(
            client = client,
            metadataMapper = NHentaiMetadataMapper(
                seriesMetadataConfig = SeriesMetadataConfig(),
                bookMetadataConfig = BookMetadataConfig(),
                authorRoles = listOf(AuthorRole.WRITER),
                artistRoles = listOf(AuthorRole.PENCILLER),
            ),
            nameMatcher = nameSimilarityMatcher(EXACT),
            fetchSeriesCovers = false,
            fetchBookCovers = false,
        )

        val metadata = provider.getSeriesMetadata(ProviderSeriesId("https://nhentai.net/g/123456/"))

        val url = assertNotNull(capturedUrl)
        assertEquals("/api/gallery/123456", url.encodedPath)
        assertEquals(ProviderSeriesId("123456"), metadata.id)
        assertEquals("Pretty Title", metadata.metadata.title?.name)
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
    }
}
