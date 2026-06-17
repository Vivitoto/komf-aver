package snd.komf.providers.nhentai

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import snd.komf.model.Author
import snd.komf.model.AuthorRole
import snd.komf.model.BookMetadata
import snd.komf.model.BookRange
import snd.komf.model.Image
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.ReleaseDate
import snd.komf.model.SeriesBook
import snd.komf.model.SeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.model.SeriesTitle
import snd.komf.model.TitleType.LOCALIZED
import snd.komf.model.TitleType.NATIVE
import snd.komf.model.WebLink
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataConfigApplier
import snd.komf.providers.SeriesMetadataConfig
import kotlin.time.Instant

class NHentaiMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
    private val authorRoles: Collection<AuthorRole>,
    private val artistRoles: Collection<AuthorRole>,
) {

    fun toSeriesMetadata(gallery: NHentaiGallery, cover: Image? = null): ProviderSeriesMetadata {
        val titles = gallery.toTitles()
        val link = WebLink("nHentai", gallery.galleryUrl())
        val metadata = SeriesMetadata(
            title = titles.firstOrNull(),
            titles = titles,
            language = gallery.languageIso(),
            tags = gallery.toTags(),
            authors = gallery.toAuthors(),
            releaseDate = gallery.uploadDate?.toReleaseDate(),
            totalBookCount = 1,
            links = listOf(link),
            thumbnail = cover,
        )

        return MetadataConfigApplier.apply(
            ProviderSeriesMetadata(
                id = ProviderSeriesId(gallery.id.toString()),
                metadata = metadata,
                books = listOf(gallery.toSeriesBook())
            ),
            seriesMetadataConfig
        )
    }

    fun toBookMetadata(gallery: NHentaiGallery, cover: Image? = null): ProviderBookMetadata {
        val metadata = BookMetadata(
            title = gallery.mainTitle(),
            number = BookRange(1),
            numberSort = 1.0,
            releaseDate = gallery.uploadDate?.toLocalDate(),
            authors = gallery.toAuthors(),
            tags = gallery.toTags().toSet(),
            links = listOf(WebLink("nHentai", gallery.galleryUrl())),
            thumbnail = cover,
        )

        return MetadataConfigApplier.apply(
            ProviderBookMetadata(
                id = ProviderBookId(gallery.id.toString()),
                seriesId = ProviderSeriesId(gallery.id.toString()),
                metadata = metadata,
            ),
            bookMetadataConfig
        )
    }

    fun toSeriesSearchResult(gallery: NHentaiGallery): SeriesSearchResult {
        return SeriesSearchResult(
            url = gallery.galleryUrl(),
            imageUrl = gallery.coverUrl(),
            title = gallery.mainTitle(),
            provider = CoreProviders.NHENTAI,
            resultId = gallery.id.toString(),
        )
    }

    private fun NHentaiGallery.toSeriesBook(): SeriesBook {
        return SeriesBook(
            id = ProviderBookId(id.toString()),
            number = BookRange(1),
            name = mainTitle(),
            type = null,
            edition = null,
        )
    }

    private fun NHentaiGallery.toTags(): List<String> {
        val mappedTags = tags.mapNotNull { tag ->
            val namespace = when (tag.type.lowercase()) {
                "artist", "group", "parody", "character", "language", "category", "tag" -> tag.type.lowercase()
                else -> return@mapNotNull null
            }
            "$namespace:${tag.name}"
        }

        return (mappedTags + "source:nhentai.net/g/$id").distinct()
    }

    private fun NHentaiGallery.toAuthors(): List<Author> {
        val roles = (authorRoles + artistRoles).distinct()
        return tags
            .filter { it.type.equals("artist", ignoreCase = true) }
            .flatMap { tag -> roles.map { role -> Author(tag.name, role) } }
            .distinct()
    }

    private fun NHentaiGallery.toTitles(): List<SeriesTitle> {
        return buildList {
            title.pretty?.ifBlank { null }?.let { add(SeriesTitle(it, null, languageIso())) }
            title.english?.ifBlank { null }?.let { add(SeriesTitle(it, LOCALIZED, "en")) }
            title.japanese?.ifBlank { null }?.let { add(SeriesTitle(it, NATIVE, "ja")) }
        }.distinctBy { it.name }
    }

    private fun NHentaiGallery.mainTitle(): String {
        return title.pretty?.ifBlank { null }
            ?: title.english?.ifBlank { null }
            ?: title.japanese?.ifBlank { null }
            ?: id.toString()
    }

    private fun NHentaiGallery.languageIso(): String? {
        val languageTag = tags.firstOrNull { it.type.equals("language", ignoreCase = true) }?.name
        return when (languageTag?.lowercase()) {
            "english" -> "en"
            "japanese" -> "ja"
            "chinese" -> "zh"
            "korean" -> "ko"
            "spanish" -> "es"
            "french" -> "fr"
            "portuguese" -> "pt"
            "italian" -> "it"
            "german" -> "de"
            "russian" -> "ru"
            "polish" -> "pl"
            "thai" -> "th"
            "vietnamese" -> "vi"
            "indonesian" -> "id"
            "dutch" -> "nl"
            else -> null
        }
    }

    private fun Long.toReleaseDate(): ReleaseDate {
        val date = toLocalDate()
        return ReleaseDate(
            year = date.year,
            month = date.month.number,
            day = date.day,
        )
    }

    private fun Long.toLocalDate() = Instant.fromEpochSeconds(this)
        .toLocalDateTime(TimeZone.UTC)
        .date
}
