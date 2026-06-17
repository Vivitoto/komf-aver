package snd.komf.providers.nhentai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import snd.komf.model.Image

private val logger = KotlinLogging.logger {}

class NHentaiClient(private val ktor: HttpClient) {
    private val apiBaseUrl = "https://nhentai.net/api"

    suspend fun search(query: String, limit: Int): List<NHentaiGallery> {
        return withProviderLogging(action = "search", host = "nhentai.net") {
            ktor.get("$apiBaseUrl/galleries/search") {
                parameter("query", query)
            }.body<NHentaiSearchResponse>().results.take(limit)
        }
    }

    suspend fun getGallery(id: Long): NHentaiGallery {
        return withProviderLogging(action = "getGallery", host = "nhentai.net", detail = "id=$id") {
            ktor.get("$apiBaseUrl/gallery/$id").body()
        }
    }

    suspend fun getImage(url: String): Image {
        val host = runCatching { Url(url).host }.getOrDefault("unknown")
        return withProviderLogging(action = "getImage", host = host) {
            Image(ktor.get(url).body())
        }
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
