package snd.komf.app.config

import com.charleskorn.kaml.Yaml
import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komf.flaresolverr.FlareSolverrConfig
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isReadable

private val logger = KotlinLogging.logger {}

class ConfigLoader(private val yaml: Yaml) {

    fun loadDirectory(directory: Path): AppConfig {
        val path = directory.resolve("application.yml")
        val config = yaml.decodeFromString(AppConfig.serializer(), Files.readString(path))
        return postProcessConfig(config, directory)
    }

    fun loadFile(file: Path): AppConfig {
        val config = yaml.decodeFromString(AppConfig.serializer(), Files.readString(file.toRealPath()))
        return postProcessConfig(config, null)
    }

    fun default(): AppConfig {
        val filePath = Path.of(".").toAbsolutePath().normalize().resolve("application.yml")
        return if (filePath.isReadable()) {
            val config = yaml.decodeFromString(AppConfig.serializer(), Files.readString(filePath.toRealPath()))
            postProcessConfig(config, null)
        } else {
            postProcessConfig(AppConfig(), null)
        }
    }

    private fun postProcessConfig(config: AppConfig, configDirectory: Path?): AppConfig {
        val processedConfig = overrideConfigDirAndEnvVars(config, configDirectory?.toString())

        warnAboutDisabledProviders(processedConfig)
        return processedConfig
    }

    private fun overrideConfigDirAndEnvVars(config: AppConfig, configDirectory: String?): AppConfig {
        val databaseConfig = config.database
        val databaseFile = configDirectory?.let { "$it/database.sqlite" } ?: databaseConfig.file
        val notificationConfig = config.notifications
        val appriseConfig = config.notifications.apprise
        val discordConfig = config.notifications.discord
        val templatesDirectory = configDirectory ?: notificationConfig.templatesDirectory
        val mangaBakaDirectory = configDirectory?.let { "$it/mangabaka" }
            ?: config.metadataProviders.mangabakaDatabaseDir

        val appriseUrls = System.getenv("KOMF_APPRISE_URLS")?.ifBlank { null }
            ?.split(",")?.toList()
            ?: appriseConfig.urls
        val discordWebhooks = System.getenv("KOMF_DISCORD_WEBHOOKS")?.ifBlank { null }
            ?.split(",")?.toList()
            ?: discordConfig.webhooks

        val komgaConfig = config.komga
        val komgaBaseUri = System.getenv("KOMF_KOMGA_BASE_URI")?.ifBlank { null } ?: komgaConfig.baseUri
        val komgaUser = System.getenv("KOMF_KOMGA_USER")?.ifBlank { null } ?: komgaConfig.komgaUser
        val komgaPassword = System.getenv("KOMF_KOMGA_PASSWORD")?.ifBlank { null } ?: komgaConfig.komgaPassword

        val kavitaConfig = config.kavita
        val kavitaBaseUri = System.getenv("KOMF_KAVITA_BASE_URI")?.ifBlank { null } ?: kavitaConfig.baseUri
        val kavitaApiKey = System.getenv("KOMF_KAVITA_API_KEY")?.ifBlank { null } ?: kavitaConfig.apiKey

        val serverConfig = config.server
        val serverPort = System.getenv("KOMF_SERVER_PORT")?.toIntOrNull() ?: serverConfig.port
        val logLevel = System.getenv("KOMF_LOG_LEVEL")?.ifBlank { null } ?: config.logLevel

        val metadataProvidersConfig = config.metadataProviders
        val malClientId = System.getenv("KOMF_METADATA_PROVIDERS_MAL_CLIENT_ID")?.ifBlank { null }
            ?: metadataProvidersConfig.malClientId
        val comicVineApiKey = System.getenv("KOMF_METADATA_PROVIDERS_COMIC_VINE_API_KEY")?.ifBlank { null }
            ?: metadataProvidersConfig.comicVineApiKey
        val comicVineSearchLimit = System.getenv("KOMF_METADATA_PROVIDERS_COMIC_VINE_SEARCH_LIMIT")?.ifBlank { null }
            ?: metadataProvidersConfig.comicVineSearchLimit
        val bangumiToken = System.getenv("KOMF_METADATA_PROVIDERS_BANGUMI_TOKEN")?.ifBlank { null }
            ?: metadataProvidersConfig.bangumiToken
        val flareSolverrConfig = resolveFlareSolverrConfig(config.flareSolverr)

        return config.copy(
            komga = komgaConfig.copy(
                baseUri = komgaBaseUri,
                komgaUser = komgaUser,
                komgaPassword = komgaPassword
            ),
            kavita = kavitaConfig.copy(
                baseUri = kavitaBaseUri,
                apiKey = kavitaApiKey,
            ),

            database = databaseConfig.copy(
                file = databaseFile
            ),
            metadataProviders = metadataProvidersConfig.copy(
                malClientId = malClientId,
                comicVineApiKey = comicVineApiKey,
                bangumiToken = bangumiToken,
                mangabakaDatabaseDir = mangaBakaDirectory
            ),
            notifications = config.notifications.copy(
                templatesDirectory = templatesDirectory,
                apprise = config.notifications.apprise.copy(
                    urls = appriseUrls
                ),
                discord = config.notifications.discord.copy(
                    webhooks = discordWebhooks
                )
            ),
            server = serverConfig.copy(port = serverPort),
            flareSolverr = flareSolverrConfig,
            logLevel = logLevel
        )
    }

    fun resolveProxyConfig(config: ProxyConfig): ProxyConfig {
        val komfProxyUrl = getenv("KOMF_PROXY_URL")
        val standardProxyUrl = getenvFirst(
            "HTTPS_PROXY",
            "https_proxy",
            "HTTP_PROXY",
            "http_proxy",
            "ALL_PROXY",
            "all_proxy"
        )
        val proxyUrl = komfProxyUrl ?: config.url ?: standardProxyUrl
        val proxyEnabled = getenv("KOMF_PROXY_ENABLED")
            ?.lowercase()
            ?.toBooleanStrictOrNull()
            ?: (config.enabled || komfProxyUrl != null || standardProxyUrl != null)
        val proxyUrlCredentials = parseProxyUrlCredentials(proxyUrl)
        val proxyUsername = getenv("KOMF_PROXY_USERNAME") ?: config.username ?: proxyUrlCredentials?.username
        val proxyPassword = getenv("KOMF_PROXY_PASSWORD") ?: config.password ?: proxyUrlCredentials?.password
        val proxyNonProxyHosts = parseCommaSeparated(getenv("KOMF_PROXY_NON_PROXY_HOSTS"))
            ?: (config.nonProxyHosts + parseCommaSeparated(getenvFirst("NO_PROXY", "no_proxy")).orEmpty()).distinct()

        return config.copy(
            enabled = proxyEnabled,
            url = proxyUrl,
            username = proxyUsername,
            password = proxyPassword,
            nonProxyHosts = proxyNonProxyHosts,
        )
    }

    private fun getenv(name: String): String? = System.getenv(name)?.ifBlank { null }

    private fun getenvFirst(vararg names: String): String? {
        return names.firstNotNullOfOrNull { getenv(it) }
    }

    private fun parseCommaSeparated(value: String?): List<String>? {
        return value
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
    }

    private fun warnAboutDisabledProviders(config: AppConfig) {
        if (
            config.metadataProviders.defaultProviders.mangaUpdates.enabled.not() &&
            config.metadataProviders.defaultProviders.mal.enabled.not() &&
            config.metadataProviders.defaultProviders.nautiljon.enabled.not() &&
            config.metadataProviders.defaultProviders.aniList.enabled.not() &&
            config.metadataProviders.defaultProviders.yenPress.enabled.not() &&
            config.metadataProviders.defaultProviders.kodansha.enabled.not() &&
            config.metadataProviders.defaultProviders.viz.enabled.not() &&
            config.metadataProviders.defaultProviders.bookWalker.enabled.not() &&
            config.metadataProviders.defaultProviders.mangaDex.enabled.not() &&
            config.metadataProviders.defaultProviders.bangumi.enabled.not() &&
            config.metadataProviders.defaultProviders.comicVine.enabled.not() &&
            config.metadataProviders.defaultProviders.hentag.enabled.not() &&
            config.metadataProviders.defaultProviders.nhentai.enabled.not() &&
            config.metadataProviders.defaultProviders.ehentai.enabled.not() &&
            config.metadataProviders.defaultProviders.mangaBaka.enabled.not() &&
            config.metadataProviders.defaultProviders.webtoons.enabled.not() &&
            config.metadataProviders.libraryProviders.isEmpty()
        ) {
            logger.warn { "No metadata providers enabled. You will not be able to get new metadata" }
        }
    }
}

internal fun resolveFlareSolverrConfig(
    config: FlareSolverrConfig,
    getenv: (String) -> String? = { System.getenv(it)?.ifBlank { null } },
): FlareSolverrConfig {
    val maxTimeout = getenv("KOMF_FLARESOLVERR_MAX_TIMEOUT")?.toLongOrNull()
        ?: getenv("KOMF_FLARESOLVERR_MAX_TIMEOUT_MS")?.toLongOrNull()
        ?: config.maxTimeout

    return config.copy(
        enabled = getenv("KOMF_FLARESOLVERR_ENABLED")
            ?.lowercase()
            ?.toBooleanStrictOrNull()
            ?: config.enabled,
        url = getenv("KOMF_FLARESOLVERR_URL") ?: config.url,
        timeoutSeconds = getenv("KOMF_FLARESOLVERR_TIMEOUT_SECONDS")?.toLongOrNull()
            ?: config.timeoutSeconds,
        maxTimeout = maxTimeout,
        session = getenv("KOMF_FLARESOLVERR_SESSION") ?: config.session,
    )
}

internal data class ProxyUrlCredentials(
    val username: String?,
    val password: String?,
)

internal fun parseProxyUrlCredentials(url: String?): ProxyUrlCredentials? {
    val rawUserInfo = try {
        URI(url?.trim().orEmpty()).rawUserInfo
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null

    val userInfo = rawUserInfo.split(":", limit = 2)
    val username = userInfo.getOrNull(0)?.decodeUriUserInfoPart()?.ifBlank { null }
    val password = userInfo.getOrNull(1)?.decodeUriUserInfoPart()
    if (username == null && password.isNullOrBlank()) return null

    return ProxyUrlCredentials(username, password)
}

private fun String.decodeUriUserInfoPart(): String? {
    return try {
        URLDecoder.decode(replace("+", "%2B"), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
