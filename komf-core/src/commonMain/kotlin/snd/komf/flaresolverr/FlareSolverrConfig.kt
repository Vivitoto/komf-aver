package snd.komf.flaresolverr

import kotlinx.serialization.Serializable

@Serializable
data class FlareSolverrConfig(
    val enabled: Boolean = false,
    val url: String? = null,
    val timeoutSeconds: Long? = null,
    val maxTimeout: Long? = null,
    val session: String? = null,
) {
    fun maxTimeoutMillis(): Long = maxTimeout
        ?: timeoutSeconds?.let { it * 1000 }
        ?: DEFAULT_MAX_TIMEOUT_MILLIS

    companion object {
        const val DEFAULT_MAX_TIMEOUT_MILLIS = 60_000L
    }
}
