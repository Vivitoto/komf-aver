package snd.komf.providers.nhentai

import io.github.reactivecircus.cache4k.Cache
import snd.komf.model.Image
import snd.komf.model.MatchQuery
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataProvider
import snd.komf.util.NameSimilarityMatcher
import kotlin.time.Duration.Companion.minutes

class NHentaiMetadataProvider(
    private val client: NHentaiClient,
    private val metadataMapper: NHentaiMetadataMapper,
    private val nameMatcher: NameSimilarityMatcher,
    private val fetchSeriesCovers: Boolean,
    private val fetchBookCovers: Boolean,
) : MetadataProvider {

    private val cache = Cache.Builder<ProviderSeriesId, NHentaiGallery>()
        .expireAfterWrite(10.minutes)
        .build()

    override fun providerName() = CoreProviders.NHENTAI

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val gallery = getGallery(seriesId)
        val cover = if (fetchSeriesCovers) getCover(gallery) else null
        return metadataMapper.toSeriesMetadata(gallery, cover)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? {
        return getCover(getGallery(seriesId))
    }

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val gallery = getGallery(seriesId)
        val cover = if (fetchBookCovers) getCover(gallery) else null
        return metadataMapper.toBookMetadata(gallery, cover)
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> {
        return client.search(seriesName.take(400), limit)
            .map { gallery ->
                metadataMapper.toSeriesSearchResult(gallery)
                    .also { cache.put(ProviderSeriesId(it.resultId), gallery) }
            }
    }

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        val directId = NHentaiIdParser.extractId(
            matchQuery.seriesName,
            matchQuery.seriesFolder,
            matchQuery.bookQualifier?.name,
        )
        if (directId != null) {
            val gallery = getGallery(ProviderSeriesId(directId.toString()))
            val cover = if (fetchSeriesCovers) getCover(gallery) else null
            return metadataMapper.toSeriesMetadata(gallery, cover)
        }

        val seriesName = matchQuery.seriesName
        return client.search(seriesName.take(400), 10)
            .firstOrNull { result -> matchesName(seriesName, result.titleCandidates()) }
            ?.let { gallery ->
                cache.put(ProviderSeriesId(gallery.id.toString()), gallery)
                val cover = if (fetchSeriesCovers) getCover(gallery) else null
                metadataMapper.toSeriesMetadata(gallery, cover)
            }
    }

    private suspend fun getGallery(seriesId: ProviderSeriesId): NHentaiGallery {
        val id = NHentaiIdParser.extractId(seriesId.value)
            ?: error("Invalid nHentai gallery id: ${seriesId.value}")
        return cache.get(ProviderSeriesId(id.toString())) { client.getGallery(id) }
    }

    private suspend fun getCover(gallery: NHentaiGallery): Image? {
        return gallery.coverUrl()?.let { client.getImage(it) }
    }

    private fun matchesName(name: String, titles: Collection<String>): Boolean {
        return nameMatcher.matches(name, titles) ||
                nameMatcher.matches(removeParentheses(name), titles.map(::removeParentheses))
    }

    private fun NHentaiGallery.titleCandidates(): List<String> {
        return listOfNotNull(
            title.pretty?.ifBlank { null },
            title.english?.ifBlank { null },
            title.japanese?.ifBlank { null },
        )
    }

    private fun removeParentheses(name: String): String {
        return name.replace("[(\\[{]([^)\\]}]+)[)\\]}]".toRegex(), "").trim()
    }
}
