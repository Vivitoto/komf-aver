package snd.komf.providers.nhentai

import snd.komf.model.AuthorRole.PENCILLER
import snd.komf.model.AuthorRole.WRITER
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.SeriesMetadataConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NHentaiMetadataMapperTest {

    @Test
    fun extractsDirectIds() {
        assertEquals(123456L, NHentaiIdParser.extractId("https://nhentai.net/g/123456/"))
        assertEquals(123456L, NHentaiIdParser.extractId("nhentai:123456"))
        assertEquals(123456L, NHentaiIdParser.extractId("123456"))
    }

    @Test
    fun mapsNamespacedTagsAndArtistAuthors() {
        val gallery = NHentaiGallery(
            id = 123456,
            title = NHentaiTitle(pretty = "Pretty Title", english = "English Title"),
            tags = listOf(
                NHentaiTag(type = "artist", name = "artist name"),
                NHentaiTag(type = "group", name = "group name"),
                NHentaiTag(type = "parody", name = "parody name"),
                NHentaiTag(type = "character", name = "character name"),
                NHentaiTag(type = "language", name = "english"),
                NHentaiTag(type = "category", name = "doujinshi"),
                NHentaiTag(type = "tag", name = "sample tag"),
            ),
        )
        val mapper = NHentaiMetadataMapper(
            seriesMetadataConfig = SeriesMetadataConfig(),
            bookMetadataConfig = BookMetadataConfig(),
            authorRoles = listOf(WRITER),
            artistRoles = listOf(PENCILLER),
        )

        val metadata = mapper.toSeriesMetadata(gallery).metadata

        assertEquals("en", metadata.language)
        assertTrue("artist:artist name" in metadata.tags)
        assertTrue("group:group name" in metadata.tags)
        assertTrue("parody:parody name" in metadata.tags)
        assertTrue("character:character name" in metadata.tags)
        assertTrue("language:english" in metadata.tags)
        assertTrue("category:doujinshi" in metadata.tags)
        assertTrue("tag:sample tag" in metadata.tags)
        assertTrue("source:nhentai.net/g/123456" in metadata.tags)
        assertTrue(metadata.authors.any { it.name == "artist name" && it.role == WRITER })
        assertTrue(metadata.authors.any { it.name == "artist name" && it.role == PENCILLER })
    }
}
