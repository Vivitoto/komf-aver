package snd.komf.providers.ehentai

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

class EHentaiParser {

    fun parseSearchResults(html: String, baseWebUrl: String): List<EHentaiSearchResult> {
        val document = Ksoup.parse(html)
        return document.getElementsByTag("a")
            .mapNotNull { anchor ->
                val href = anchor.attr("href")
                val id = EHentaiIdParser.extractId(href) ?: return@mapNotNull null
                val title = anchor.visibleGalleryTitle() ?: return@mapNotNull null
                EHentaiSearchResult(
                    id = id,
                    title = title,
                    thumbnailUrl = anchor.thumbnailUrl(),
                    url = "$baseWebUrl/g/${id.gid}/${id.token}/",
                )
            }
            .distinctBy { it.id }
    }

    private fun Element.visibleGalleryTitle(): String? {
        return getElementsByClass("glink")
            .firstOrNull()
            ?.text()
            ?.ifBlank { null }
            ?: attr("title").ifBlank { null }
            ?: text().trim().ifBlank { null }
    }

    private fun Element.thumbnailUrl(): String? {
        val image = getElementsByTag("img").firstOrNull() ?: return null
        return image.attr("data-src").ifBlank { null }
            ?: image.attr("src").ifBlank { null }
    }

    companion object {
        const val PUBLIC_BASE_URL = "https://e-hentai.org"
        const val EX_BASE_URL = "https://exhentai.org"
    }
}
