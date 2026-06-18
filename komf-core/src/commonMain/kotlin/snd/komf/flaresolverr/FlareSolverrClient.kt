package snd.komf.flaresolverr

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface FlareSolverr {
    val enabled: Boolean

    suspend fun requestGet(
        url: String,
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
    ): FlareSolverrSolution?
}

object DisabledFlareSolverr : FlareSolverr {
    override val enabled: Boolean = false

    override suspend fun requestGet(
        url: String,
        headers: Map<String, String>,
        cookies: Map<String, String>,
    ): FlareSolverrSolution? = null
}

class KtorFlareSolverrClient(
    private val ktor: HttpClient,
    private val config: FlareSolverrConfig,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    },
) : FlareSolverr {
    private val endpoint = config.url?.trimEnd('/')?.let { "$it/v1" }

    override val enabled: Boolean = config.enabled && !endpoint.isNullOrBlank()

    override suspend fun requestGet(
        url: String,
        headers: Map<String, String>,
        cookies: Map<String, String>,
    ): FlareSolverrSolution? {
        if (!enabled) return null

        val request = FlareSolverrRequest(
            command = "request.get",
            url = url,
            maxTimeout = config.maxTimeoutMillis(),
            session = config.session?.ifBlank { null },
            headers = headers.filterValues { it.isNotBlank() },
            cookies = cookies
                .filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() }
                .map { (name, value) -> FlareSolverrCookie(name = name, value = value) },
        )
        val responseText = ktor.post(endpoint!!) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }.bodyAsText()
        val response = json.decodeFromString<FlareSolverrResponse>(responseText)

        check(response.status.equals("ok", ignoreCase = true)) {
            "FlareSolverr request failed: ${response.message.orEmpty()}"
        }
        return requireNotNull(response.solution) {
            "FlareSolverr response did not include a solution"
        }
    }
}

@Serializable
data class FlareSolverrSolution(
    val url: String? = null,
    val status: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val response: String? = null,
    val cookies: List<FlareSolverrCookie> = emptyList(),
    val userAgent: String? = null,
)

@Serializable
data class FlareSolverrCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expires: Long? = null,
    val httpOnly: Boolean? = null,
    val secure: Boolean? = null,
    val sameSite: String? = null,
)

@Serializable
private data class FlareSolverrRequest(
    @SerialName("cmd")
    val command: String,
    val url: String,
    val maxTimeout: Long,
    val session: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val cookies: List<FlareSolverrCookie> = emptyList(),
)

@Serializable
private data class FlareSolverrResponse(
    val status: String,
    val message: String? = null,
    val solution: FlareSolverrSolution? = null,
)
