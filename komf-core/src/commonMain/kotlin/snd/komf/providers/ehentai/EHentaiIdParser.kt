package snd.komf.providers.ehentai

object EHentaiIdParser {
    private val urlRegex =
        "(?i)(?:https?://)?(?:e-hentai|exhentai)\\.org/g/(\\d+)/([0-9a-z]+)/?".toRegex()
    private val plainIdRegex = "^\\s*(\\d{2,})/([0-9a-z]{6,})\\s*$".toRegex(RegexOption.IGNORE_CASE)

    fun extractId(vararg candidates: String?): EHentaiGalleryId? {
        return candidates.asSequence()
            .filterNotNull()
            .mapNotNull { extractId(it) }
            .firstOrNull()
    }

    fun extractId(candidate: String): EHentaiGalleryId? {
        val match = urlRegex.find(candidate) ?: plainIdRegex.find(candidate) ?: return null
        return EHentaiGalleryId(
            gid = match.groupValues[1].toLongOrNull() ?: return null,
            token = match.groupValues[2],
        )
    }
}
