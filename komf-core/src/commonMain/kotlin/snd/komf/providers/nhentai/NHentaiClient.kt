package snd.komf.providers.nhentai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import snd.komf.flaresolverr.CloudflareChallengeDetector
import snd.komf.flaresolverr.CloudflareChallengeException
import snd.komf.flaresolverr.DisabledFlareSolverr
import snd.komf.flaresolverr.FlareSolverr
import snd.komf.model.Image

private val logger = KotlinLogging.logger {}

class NHentaiClient(
    private val ktor: HttpClient,
    private val flareSolverr: FlareSolverr = DisabledFlareSolverr,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    },
) {
    private val apiBaseUrl = "https://nhentai.net/api"

    suspend fun search(query: String, limit: Int): List<NHentaiGallery> {
        return withProviderLogging(action = "search", host = "nhentai.net") {
            getJson<NHentaiSearchResponse>("$apiBaseUrl/galleries/search", action = "search") {
                parameter("query", query)
            }.results.take(limit)
        }
    }

    suspend fun getGallery(id: Long): NHentaiGallery {
        return withProviderLogging(action = "getGallery", host = "nhentai.net", detail = "id=$id") {
            getJson("$apiBaseUrl/gallery/$id", action = "getGallery")
        }
    }

    suspend fun getImage(url: String): Image {
        val host = runCatching { Url(url).host }.getOrDefault("unknown")
        return withProviderLogging(action = "getImage", host = host) {
            Image(ktor.get(url).body())
        }
    }

    private suspend inline fun <reified T> getJson(
        url: String,
        action: String,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = getTextWithCloudflareFallback(url, action, configure)
        val body = response.body
        return try {
            json.decodeFromString<T>(body)
        } catch (exception: SerializationException) {
            if (CloudflareChallengeDetector.isChallengeHtml(body)) {
                val fallbackBody = requestViaFlareSolverr(response.requestUrl, action)
                    ?: throw CloudflareChallengeException(
                        "nHentai response was blocked by Cloudflare and FlareSolverr is disabled"
                    )
                json.decodeFromString<T>(fallbackBody)
            } else {
                throw exception
            }
        }
    }

    private suspend fun getTextWithCloudflareFallback(
        url: String,
        action: String,
        configure: HttpRequestBuilder.() -> Unit,
    ): TextResponse {
        var requestUrl = url
        val body = try {
            val response = ktor.get(url) {
                configure()
            }
            requestUrl = response.call.request.url.toString()
            response.bodyAsText()
        } catch (exception: ResponseException) {
            if (CloudflareChallengeDetector.isBlockedStatus(exception.response.status.value)) {
                val resolvedUrl = exception.response.call.request.url.toString()
                return TextResponse(requestViaFlareSolverr(resolvedUrl, action) ?: throw exception, resolvedUrl)
            }
            throw exception
        }

        return if (CloudflareChallengeDetector.isChallengeHtml(body)) {
            TextResponse(
                requestViaFlareSolverr(requestUrl, action)
                ?: throw CloudflareChallengeException(
                    "nHentai response was blocked by Cloudflare and FlareSolverr is disabled"
                ),
                requestUrl,
            )
        } else {
            TextResponse(body, requestUrl)
        }
    }

    private suspend fun requestViaFlareSolverr(url: String, action: String): String? {
        val solution = flareSolverr.requestGet(url) ?: return null
        val response = requireNotNull(solution.response) {
            "FlareSolverr returned no response body for nHentai $action"
        }
        if (CloudflareChallengeDetector.isChallengeHtml(response)) {
            throw CloudflareChallengeException("FlareSolverr returned a Cloudflare challenge for nHentai $action")
        }
        logger.info { "provider=NHENTAI action=$action host=nhentai.net result=flaresolverr-fallback-success" }
        return response
    }

    private suspend fun <T> withProviderLogging(
        action: String,
        host: String,
        detail: String? = null,
        block: suspend () -> T,
    ): T {
        logger.debug { "provider=NHENTAI action=$action host=$host${detail.formatLogDetail()} result=start" }
        return try {
            block().also {
                logger.debug { "provider=NHENTAI action=$action host=$host${detail.formatLogDetail()} result=success" }
            }
        } catch (exception: ResponseException) {
            val status = exception.response.status.value
            logger.warn {
                "provider=NHENTAI action=$action host=$host status=$status result=failed hint=${status.nhentaiHint()}"
            }
            throw exception
        } catch (exception: SerializationException) {
            logger.warn {
                "provider=NHENTAI action=$action host=$host result=failed hint=response-json-unexpected-or-block-page"
            }
            throw exception
        } catch (exception: CloudflareChallengeException) {
            logger.warn {
                "provider=NHENTAI action=$action host=$host result=failed hint=cloudflare-challenge-flaresolverr-disabled-or-unsolved"
            }
            throw exception
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.warn {
                "provider=NHENTAI action=$action host=$host result=failed exception=${exception::class.simpleName ?: "Exception"} hint=network-proxy-dns-or-provider-change"
            }
            throw exception
        }
    }

    private fun String?.formatLogDetail(): String = this?.let { " $it" }.orEmpty()

    private data class TextResponse(
        val body: String,
        val requestUrl: String,
    )

    private fun Int.nhentaiHint(): String {
        return when (this) {
            401, 403 -> "access-denied-cloudflare-region-or-provider-block"
            404 -> "gallery-not-found-or-removed"
            429 -> "rate-limited"
            503 -> "service-unavailable-or-cloudflare-challenge"
            in 500..599 -> "provider-server-error"
            else -> "http-error"
        }
    }
}
