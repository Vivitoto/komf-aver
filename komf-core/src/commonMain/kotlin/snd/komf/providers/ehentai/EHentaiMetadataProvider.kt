package snd.komf.providers.ehentai

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

class EHentaiMetadataProvider(
    private val client: EHentaiClient,
    private val metadataMapper: EHentaiMetadataMapper,
    private val nameMatcher: NameSimilarityMatcher,
    private val fetchSeriesCovers: Boolean,
    private val fetchBookCovers: Boolean,
) : MetadataProvider {

    private val cache = Cache.Builder<ProviderSeriesId, EHentaiGallery>()
        .expireAfterWrite(10.minutes)
        .build()

    override fun providerName() = CoreProviders.EHENTAI

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
            .map(metadataMapper::toSeriesSearchResult)
    }

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        val directId = EHentaiIdParser.extractId(
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
            .firstOrNull { result -> matchesName(seriesName, result.title) }
            ?.let { result ->
                val gallery = getGallery(ProviderSeriesId(result.id.toString()))
                val cover = if (fetchSeriesCovers) getCover(gallery) else null
                metadataMapper.toSeriesMetadata(gallery, cover)
            }
    }

    private suspend fun getGallery(seriesId: ProviderSeriesId): EHentaiGallery {
        return cache.get(seriesId) {
            val id = EHentaiIdParser.extractId(seriesId.value)
                ?: error("Invalid E-Hentai gallery id '${seriesId.value}'. Expected gid/token")
            client.getGallery(id)
        }
    }

    private suspend fun getCover(gallery: EHentaiGallery): Image? {
        return gallery.thumb?.let { client.getImage(it) }
    }

    private fun matchesName(name: String, title: String): Boolean {
        return nameMatcher.matches(name, title) ||
                nameMatcher.matches(removeParentheses(name), removeParentheses(title))
    }

    private fun removeParentheses(name: String): String {
        return name.replace("[(\\[{]([^)\\]}]+)[)\\]}]".toRegex(), "").trim()
    }
}
