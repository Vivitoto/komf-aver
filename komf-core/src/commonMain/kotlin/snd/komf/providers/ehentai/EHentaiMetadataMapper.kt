package snd.komf.providers.ehentai

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
import snd.komf.model.TitleType.NATIVE
import snd.komf.model.WebLink
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataConfigApplier
import snd.komf.providers.SeriesMetadataConfig
import kotlin.time.Instant

class EHentaiMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
    private val authorRoles: Collection<AuthorRole>,
    private val artistRoles: Collection<AuthorRole>,
    private val webBaseUrl: String,
) {

    fun toSeriesMetadata(gallery: EHentaiGallery, cover: Image? = null): ProviderSeriesMetadata {
        val titles = gallery.toTitles()
        val metadata = SeriesMetadata(
            title = titles.firstOrNull(),
            titles = titles,
            language = gallery.languageIso(),
            genres = gallery.category?.let { listOf(it) } ?: emptyList(),
            tags = gallery.toTags(),
            totalBookCount = 1,
            authors = gallery.toAuthors(),
            releaseDate = gallery.posted?.toLongOrNull()?.toReleaseDate(),
            links = listOf(WebLink("E-Hentai", gallery.galleryUrl(webBaseUrl))),
            score = gallery.rating?.toDoubleOrNull(),
            thumbnail = cover,
        )

        return MetadataConfigApplier.apply(
            ProviderSeriesMetadata(
                id = ProviderSeriesId(gallery.id().toString()),
                metadata = metadata,
                books = listOf(gallery.toSeriesBook()),
            ),
            seriesMetadataConfig
        )
    }

    fun toBookMetadata(gallery: EHentaiGallery, cover: Image? = null): ProviderBookMetadata {
        val metadata = BookMetadata(
            title = gallery.title,
            number = BookRange(1),
            numberSort = 1.0,
            releaseDate = gallery.posted?.toLongOrNull()?.toLocalDate(),
            authors = gallery.toAuthors(),
            tags = gallery.toTags().toSet(),
            links = listOf(WebLink("E-Hentai", gallery.galleryUrl(webBaseUrl))),
            thumbnail = cover,
        )

        return MetadataConfigApplier.apply(
            ProviderBookMetadata(
                id = ProviderBookId(gallery.id().toString()),
                seriesId = ProviderSeriesId(gallery.id().toString()),
                metadata = metadata,
            ),
            bookMetadataConfig
        )
    }

    fun toSeriesSearchResult(result: EHentaiSearchResult): SeriesSearchResult {
        return SeriesSearchResult(
            url = result.url,
            imageUrl = result.thumbnailUrl,
            title = result.title,
            provider = CoreProviders.EHENTAI,
            resultId = result.id.toString(),
        )
    }

    private fun EHentaiGallery.toSeriesBook(): SeriesBook {
        return SeriesBook(
            id = ProviderBookId(id().toString()),
            number = BookRange(1),
            name = title,
            type = null,
            edition = null,
        )
    }

    private fun EHentaiGallery.toTags(): List<String> {
        val namespacedTags = tags.map { tag ->
            if (tag.contains(":")) tag else "tag:$tag"
        }
        val categoryTag = category?.ifBlank { null }?.let { "category:${it.lowercase()}" }
        val uploaderTag = uploader?.ifBlank { null }?.let { "uploader:$it" }

        return (namespacedTags + listOfNotNull(categoryTag, uploaderTag) + sourceTag()).distinct()
    }

    private fun EHentaiGallery.toAuthors(): List<Author> {
        val roles = (authorRoles + artistRoles).distinct()
        return tags
            .mapNotNull { tag ->
                if (tag.startsWith("artist:", ignoreCase = true)) tag.substringAfter(":")
                else null
            }
            .flatMap { name -> roles.map { role -> Author(name, role) } }
            .distinct()
    }

    private fun EHentaiGallery.toTitles(): List<SeriesTitle> {
        return buildList {
            title.ifBlank { null }?.let { add(SeriesTitle(it, null, null)) }
            titleJpn?.ifBlank { null }?.let { add(SeriesTitle(it, NATIVE, "ja")) }
        }.distinctBy { it.name }
    }

    private fun EHentaiGallery.languageIso(): String? {
        val languageTag = tags.firstOrNull { it.startsWith("language:", ignoreCase = true) }
            ?.substringAfter(":")
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
