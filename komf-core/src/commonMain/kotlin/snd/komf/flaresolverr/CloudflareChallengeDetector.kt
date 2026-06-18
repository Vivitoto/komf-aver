package snd.komf.flaresolverr

class CloudflareChallengeException(message: String) : RuntimeException(message)

object CloudflareChallengeDetector {
    fun isBlockedStatus(status: Int): Boolean = status == 401 || status == 403 || status == 503

    fun isChallengeHtml(body: String): Boolean {
        val sample = body.take(16_384).lowercase()
        if (!sample.contains("<html") && !sample.trimStart().startsWith("<!doctype html")) return false

        return sample.contains("cloudflare") ||
                sample.contains("cf-chl") ||
                sample.contains("__cf_chl") ||
                sample.contains("cf-browser-verification") ||
                sample.contains("/cdn-cgi/challenge-platform/") ||
                sample.contains("checking your browser") ||
                sample.contains("just a moment") ||
                sample.contains("attention required")
    }
}
