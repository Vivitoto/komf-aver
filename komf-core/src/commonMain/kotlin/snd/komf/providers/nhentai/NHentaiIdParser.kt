package snd.komf.providers.nhentai

object NHentaiIdParser {
    private val urlRegex = "(?i)(?:https?://)?(?:www\\.)?nhentai\\.net/g/(\\d+)/?".toRegex()
    private val labeledRegex = "(?i)\\bnhentai\\b\\s*[:#-]?\\s*(\\d{2,})\\b".toRegex()
    private val plainIdRegex = "^\\s*(\\d{2,})\\s*$".toRegex()

    fun extractId(vararg candidates: String?): Long? {
        return candidates.asSequence()
            .filterNotNull()
            .mapNotNull { extractId(it) }
            .firstOrNull()
    }

    fun extractId(candidate: String): Long? {
        return urlRegex.find(candidate)?.groupValues?.get(1)?.toLongOrNull()
            ?: labeledRegex.find(candidate)?.groupValues?.get(1)?.toLongOrNull()
            ?: plainIdRegex.find(candidate)?.groupValues?.get(1)?.toLongOrNull()
    }
}
