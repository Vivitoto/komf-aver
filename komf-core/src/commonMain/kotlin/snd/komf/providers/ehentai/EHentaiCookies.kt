package snd.komf.providers.ehentai

import snd.komf.providers.EHentaiConfig

internal fun parseEHentaiCookieHeader(cookieHeader: String?): Map<String, String> {
    return cookieHeader
        ?.trim()
        ?.removeCookieHeaderName()
        ?.splitToSequence(';')
        ?.mapNotNull { part ->
            val separatorIndex = part.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null

            val name = part.substring(0, separatorIndex).trim()
            if (name.isBlank()) return@mapNotNull null

            val value = part.substring(separatorIndex + 1).trim()
            if (value.isBlank()) return@mapNotNull null

            name to value
        }
        ?.toMap()
        .orEmpty()
}

private fun String.removeCookieHeaderName(): String {
    val prefix = "Cookie:"
    return if (startsWith(prefix, ignoreCase = true)) {
        substring(prefix.length).trim()
    } else {
        this
    }
}

internal fun effectiveEHentaiCookies(
    cookieHeader: String?,
    cookies: Map<String, String>,
): Map<String, String> = (parseEHentaiCookieHeader(cookieHeader) + cookies)
    .filterValues { it.isNotBlank() }

internal fun EHentaiConfig.effectiveCookies(): Map<String, String> = effectiveEHentaiCookies(
    cookieHeader = cookieHeader,
    cookies = cookies,
)
