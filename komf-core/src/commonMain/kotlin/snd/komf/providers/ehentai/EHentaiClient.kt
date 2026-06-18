package snd.komf.providers.ehentai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import snd.komf.flaresolverr.CloudflareChallengeDetector
import snd.komf.flaresolverr.CloudflareChallengeException
import snd.komf.flaresolverr.DisabledFlareSolverr
import snd.komf.flaresolverr.FlareSolverr
import snd.komf.model.Image

private val logger = KotlinLogging.logger {}

class EHentaiClient(
    private val ktor: HttpClient,
    useExhentai: Boolean,
    cookies: Map<String, String> = emptyMap(),
    cookieHeader: String? = null,
    private val userAgent: String? = null,
    private val flareSolverr: FlareSolverr = DisabledFlareSolverr,
) {
    private val parser = EHentaiParser()
    private val effectiveCookies = effectiveEHentaiCookies(cookieHeader, cookies)
    val webBaseUrl = if (useExhentai) EHentaiParser.EX_BASE_URL else EHentaiParser.PUBLIC_BASE_URL
    private val apiUrl = "https://api.e-hentai.org/api.php"

    suspend fun search(query: String, limit: Int): List<EHentaiSearchResult> {
        return withProviderLogging(action = "search", requestUrl = webBaseUrl) {
            val html = getHtmlWithCloudflareFallback(webBaseUrl, action = "search") {
                configureMetadataRequest(webBaseUrl)
                parameter("advsearch", "1")
                parameter("f_search", query)
            }

            parser.parseSearchResults(html, webBaseUrl).take(limit)
        }
    }

    suspend fun getGallery(id: EHentaiGalleryId): EHentaiGallery {
        val response = withProviderLogging(action = "getGallery", requestUrl = apiUrl, detail = "gid=${id.gid}") {
            ktor.post(apiUrl) {
                configureMetadataRequest(apiUrl)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("method", "gdata")
                    put("namespace", 1)
                    putJsonArray("gidlist") {
                        addJsonArray {
                            add(id.gid)
                            add(id.token)
                        }
                    }
                })
            }.body<EHentaiGDataResponse>()
        }

        return response.metadata.firstOrNull()
            ?: error("E-Hentai returned no metadata for gallery $id")
    }

    suspend fun getImage(url: String): Image {
        return withProviderLogging(action = "getImage", requestUrl = url) {
            Image(ktor.get(url) {
                configureImageRequest()
            }.body())
        }
    }

    private fun HttpRequestBuilder.configureMetadataRequest(requestUrl: String) {
        configureUserAgent()
        if (Url(requestUrl).host.lowercase() in cookieHosts) {
            effectiveCookies.forEach { (name, value) -> cookie(name, value) }
        }
    }

    private fun HttpRequestBuilder.configureImageRequest() {
        configureUserAgent()
    }

    private fun HttpRequestBuilder.configureUserAgent() {
        userAgent?.let { header(HttpHeaders.UserAgent, it) }
    }

    private suspend fun getHtmlWithCloudflareFallback(
        url: String,
        action: String,
        configure: HttpRequestBuilder.() -> Unit,
    ): String {
        var requestUrl = url
        val body = try {
            val response = ktor.get(url) {
                configure()
            }
            requestUrl = response.call.request.url.toString()
            response.bodyAsText()
        } catch (exception: ResponseException) {
            if (CloudflareChallengeDetector.isBlockedStatus(exception.response.status.value)) {
                return requestViaFlareSolverr(exception.response.call.request.url.toString(), action) ?: throw exception
            }
            throw exception
        }

        return if (CloudflareChallengeDetector.isChallengeHtml(body)) {
            requestViaFlareSolverr(requestUrl, action)
                ?: throw CloudflareChallengeException(
                    "E-Hentai response was blocked by Cloudflare and FlareSolverr is disabled"
                )
        } else {
            body
        }
    }

    private suspend fun requestViaFlareSolverr(url: String, action: String): String? {
        val headers = userAgent
            ?.ifBlank { null }
            ?.let { mapOf(HttpHeaders.UserAgent to it) }
            .orEmpty()
        val solution = flareSolverr.requestGet(
            url = url,
            headers = headers,
            cookies = effectiveCookies,
        ) ?: return null
        val response = requireNotNull(solution.response) {
            "FlareSolverr returned no response body for E-Hentai $action"
        }
        if (CloudflareChallengeDetector.isChallengeHtml(response)) {
            throw CloudflareChallengeException("FlareSolverr returned a Cloudflare challenge for E-Hentai $action")
        }
        logger.info { "provider=EHENTAI action=$action host=${Url(url).host} result=flaresolverr-fallback-success" }
        return response
    }

    private suspend fun <T> withProviderLogging(
        action: String,
        requestUrl: String,
        detail: String? = null,
        block: suspend () -> T,
    ): T {
        val host = runCatching { Url(requestUrl).host }.getOrDefault("unknown")
        logger.debug { "provider=EHENTAI action=$action host=$host${detail.formatLogDetail()} result=start" }
        return try {
            block().also {
                logger.debug { "provider=EHENTAI action=$action host=$host${detail.formatLogDetail()} result=success" }
            }
        } catch (exception: ResponseException) {
            val status = exception.response.status.value
            logger.warn {
                "provider=EHENTAI action=$action host=$host status=$status result=failed hint=${status.ehentaiHint(host)}"
            }
            throw exception
        } catch (exception: SerializationException) {
            logger.warn {
                "provider=EHENTAI action=$action host=$host result=failed hint=response-json-unexpected-auth-failure-or-block-page"
            }
            throw exception
        } catch (exception: CloudflareChallengeException) {
            logger.warn {
                "provider=EHENTAI action=$action host=$host result=failed hint=cloudflare-challenge-flaresolverr-disabled-or-unsolved"
            }
            throw exception
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.warn {
                "provider=EHENTAI action=$action host=$host result=failed exception=${exception::class.simpleName ?: "Exception"} hint=network-proxy-dns-auth-or-provider-change"
            }
            throw exception
        }
    }

    private fun String?.formatLogDetail(): String = this?.let { " $it" }.orEmpty()

    private fun Int.ehentaiHint(host: String): String {
        return when (this) {
            401, 403 -> if (host == "exhentai.org") {
                "access-denied-check-exhentai-cookies-and-login-state"
            } else {
                "access-denied-cloudflare-region-auth-or-provider-block"
            }
            404 -> "gallery-not-found-or-removed"
            429 -> "rate-limited"
            503 -> "service-unavailable-or-cloudflare-challenge"
            in 500..599 -> "provider-server-error"
            else -> "http-error"
        }
    }

    private companion object {
        val cookieHosts = setOf(
            "e-hentai.org",
            "exhentai.org",
            "api.e-hentai.org",
        )
    }
}
