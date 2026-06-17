package snd.komf.providers.ehentai

import snd.komf.model.AuthorRole.PENCILLER
import snd.komf.model.AuthorRole.WRITER
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.SeriesMetadataConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EHentaiMetadataMapperTest {

    @Test
    fun extractsDirectIds() {
        val expected = EHentaiGalleryId(123456, "abcdef1234")

        assertEquals(expected, EHentaiIdParser.extractId("https://e-hentai.org/g/123456/abcdef1234/"))
        assertEquals(expected, EHentaiIdParser.extractId("https://exhentai.org/g/123456/abcdef1234/"))
        assertEquals(expected, EHentaiIdParser.extractId("123456/abcdef1234"))
    }

    @Test
    fun parsesHtmlSearchResults() {
        val html = """
            <html>
              <body>
                <a href="https://e-hentai.org/g/123456/abcdef1234/">
                  <div class="glink">Visible Gallery Title</div>
                </a>
              </body>
            </html>
        """.trimIndent()

        val results = EHentaiParser().parseSearchResults(html, EHentaiParser.PUBLIC_BASE_URL)

        assertEquals(1, results.size)
        assertEquals(EHentaiGalleryId(123456, "abcdef1234"), results.first().id)
        assertEquals("Visible Gallery Title", results.first().title)
    }

    @Test
    fun mapsNamespacedTagsAndArtistAuthors() {
        val gallery = EHentaiGallery(
            gid = 123456,
            token = "abcdef1234",
            title = "Gallery Title",
            category = "Doujinshi",
            uploader = "uploader-name",
            tags = listOf(
                "artist:artist name",
                "language:english",
                "female:sample tag",
            ),
        )
        val mapper = EHentaiMetadataMapper(
            seriesMetadataConfig = SeriesMetadataConfig(),
            bookMetadataConfig = BookMetadataConfig(),
            authorRoles = listOf(WRITER),
            artistRoles = listOf(PENCILLER),
            webBaseUrl = EHentaiParser.PUBLIC_BASE_URL,
        )

        val metadata = mapper.toSeriesMetadata(gallery).metadata

        assertEquals("en", metadata.language)
        assertTrue("artist:artist name" in metadata.tags)
        assertTrue("language:english" in metadata.tags)
        assertTrue("female:sample tag" in metadata.tags)
        assertTrue("category:doujinshi" in metadata.tags)
        assertTrue("uploader:uploader-name" in metadata.tags)
        assertTrue("source:e-hentai.org/g/123456/abcdef1234" in metadata.tags)
        assertTrue(metadata.authors.any { it.name == "artist name" && it.role == WRITER })
        assertTrue(metadata.authors.any { it.name == "artist name" && it.role == PENCILLER })
    }
}
