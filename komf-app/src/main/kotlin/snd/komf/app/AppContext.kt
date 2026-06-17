package snd.komf.app

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import snd.komf.CoreModule
import snd.komf.app.config.AppConfig
import snd.komf.app.config.ConfigLoader
import snd.komf.app.config.ConfigWriter
import snd.komf.app.config.ProxyConfig
import snd.komf.app.config.parseProxyUrlCredentials
import snd.komf.ktor.komfUserAgent
import snd.komf.mediaserver.MediaServerModule
import snd.komf.notifications.NotificationsModule
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

private val logger = KotlinLogging.logger {}

class AppContext(private val configPath: Path? = null) {
    @Volatile
    var appConfig: AppConfig
        private set

    private val reloadMutex = Mutex()

    private val ktorBaseClient: HttpClient
    private val jsonBase: Json
    private val serverModule: ServerModule

    private var providersModule: CoreModule
    private var mediaServerModule: MediaServerModule
    private var notificationsModule: NotificationsModule

    private var apiRoutesDependencies: MutableStateFlow<ApiDynamicDependencies>

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            strictMode = false
        )
    )
    private val configWriter = ConfigWriter(yaml)
    private val configLoader = ConfigLoader(yaml)

    init {
        val config = loadConfig()
        setLogLevel(config)
        appConfig = config
        val effectiveProxy = configLoader.resolveProxyConfig(config.proxy)

        val httpLogger = KotlinLogging.logger("http.logging")
        val httpLoggingInterceptor = HttpLoggingInterceptor { httpLogger.info { it } }.apply {
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Proxy-Authorization")
            redactHeader("Set-Cookie")
            setLevel(appConfig.httpLogLevel)
        }
        val baseOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .configureProxy(effectiveProxy)
            .addInterceptor(httpLoggingInterceptor)
            .cache(
                Cache(
                    directory = Path.of(System.getProperty("java.io.tmpdir"))
                        .resolve("komf").createDirectories()
                        .toFile(),
                    maxSize = 50L * 1024L * 1024L // 50 MiB
                )
            )
            .build()

        jsonBase = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        ktorBaseClient = HttpClient(OkHttp) {
            engine { preconfigured = baseOkHttpClient }
            expectSuccess = true
            install(UserAgent) { agent = komfUserAgent }
        }

        providersModule = CoreModule(
            config = config.metadataProviders,
            ktor = ktorBaseClient,
            onStateRefresh = this::refreshState,
        )
        notificationsModule = NotificationsModule(config.notifications, ktorBaseClient)

        mediaServerModule = MediaServerModule(
            komgaConfig = config.komga,
            kavitaConfig = config.kavita,
            databaseConfig = config.database,
            jsonBase = jsonBase,
            ktorBaseClient = ktorBaseClient,
            appriseService = notificationsModule.appriseService,
            discordWebhookService = notificationsModule.discordWebhookService,
            metadataProviders = providersModule.metadataProviders
        )
        this.apiRoutesDependencies = MutableStateFlow(createApiRoutesDependencies())

        serverModule = ServerModule(
            serverPort = config.server.port,
            onConfigUpdate = this::refreshState,
            dynamicDependencies = apiRoutesDependencies,
        )

        serverModule.startServer()
    }

    suspend fun refreshState() {
        reloadMutex.withLock {
            reloadModules(this.appConfig)
        }
    }

    suspend fun refreshState(newConfig: AppConfig) {
        reloadMutex.withLock {
            appConfig = newConfig
            reloadModules(newConfig)
            writeConfig(newConfig)
        }
    }

    private fun reloadModules(config: AppConfig) {
        logger.info { "Reconfiguring application state" }

        val providersModule = CoreModule(
            config = config.metadataProviders,
            ktor = ktorBaseClient,
            onStateRefresh = this::refreshState,
        )
        val notificationsModule = NotificationsModule(config.notifications, ktorBaseClient)
        val mediaServerModule = MediaServerModule(
            komgaConfig = config.komga,
            kavitaConfig = config.kavita,
            databaseConfig = config.database,
            jsonBase = jsonBase,
            ktorBaseClient = ktorBaseClient,
            appriseService = notificationsModule.appriseService,
            discordWebhookService = notificationsModule.discordWebhookService,
            metadataProviders = providersModule.metadataProviders
        )

        this.close()

        this.providersModule = providersModule
        this.notificationsModule = notificationsModule
        this.mediaServerModule = mediaServerModule
        apiRoutesDependencies.value = createApiRoutesDependencies()
    }

    private fun createApiRoutesDependencies() = ApiDynamicDependencies(
        config = this.appConfig,
        jobTracker = mediaServerModule.jobTracker,
        jobsRepository = mediaServerModule.jobRepository,
        komgaMediaServerClient = mediaServerModule.komgaClient,
        komgaMetadataServiceProvider = mediaServerModule.komgaMetadataServiceProvider,
        kavitaMediaServerClient = mediaServerModule.kavitaMediaServerClient,
        kavitaMetadataServiceProvider = mediaServerModule.kavitaMetadataServiceProvider,
        discordService = notificationsModule.discordWebhookService,
        discordRenderer = notificationsModule.discordVelocityRenderer,
        appriseService = notificationsModule.appriseService,
        appriseRenderer = notificationsModule.appriseVelocityRenderer,
        mangaBakaDownloader = providersModule.mangaBakaDatabaseDownloader,
        mangaBakaDbMetadata = providersModule.mangaBakaDbMetadata
    )

    private suspend fun writeConfig(config: AppConfig) {
        withContext(Dispatchers.IO) {
            configPath?.let { path -> configWriter.writeConfig(config, path) }
                ?: configWriter.writeConfigToDefaultPath(config)
        }
    }

    private fun close() {
        mediaServerModule.close()
    }

    private fun loadConfig(): AppConfig {
        return when {
            configPath == null -> configLoader.default()
            configPath.isDirectory() -> configLoader.loadDirectory(configPath)
            else -> configLoader.loadFile(configPath)
        }
    }

    private fun setLogLevel(config: AppConfig) {
        val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = Level.valueOf(config.logLevel.uppercase())
    }

    private fun OkHttpClient.Builder.configureProxy(config: ProxyConfig): OkHttpClient.Builder {
        val proxy = config.toJavaProxy() ?: return this
        val urlCredentials = parseProxyUrlCredentials(config.url)
        val proxyUsername = config.username?.ifBlank { null } ?: urlCredentials?.username
        val proxyPassword = config.password?.ifBlank { null } ?: urlCredentials?.password

        proxySelector(ConfigProxySelector(proxy, config.nonProxyHosts))
        if (!proxyUsername.isNullOrBlank() || !proxyPassword.isNullOrBlank()) {
            proxyAuthenticator(Authenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) return@Authenticator null

                val credential = Credentials.basic(proxyUsername.orEmpty(), proxyPassword.orEmpty())
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            })
        }

        return this
    }

    private fun ProxyConfig.toJavaProxy(): Proxy? {
        if (!enabled || url.isNullOrBlank()) return null

        val uri = try {
            URI(url.trim())
        } catch (_: IllegalArgumentException) {
            logger.warn { "Invalid proxy URL configured; outbound proxy disabled" }
            return null
        }

        val scheme = uri.scheme?.lowercase()
        val type = when (scheme) {
            "http", "https" -> Proxy.Type.HTTP
            "socks", "socks4", "socks5" -> Proxy.Type.SOCKS
            else -> {
                logger.warn { "Unsupported proxy scheme configured; outbound proxy disabled" }
                return null
            }
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            logger.warn { "Proxy URL must include a host; outbound proxy disabled" }
            return null
        }

        val port = if (uri.port > 0) {
            uri.port
        } else {
            when (scheme) {
                "http" -> 80
                "https" -> 443
                "socks", "socks4", "socks5" -> 1080
                else -> -1
            }
        }

        if (port <= 0) {
            logger.warn { "Proxy URL must include a valid port; outbound proxy disabled" }
            return null
        }

        return Proxy(type, InetSocketAddress.createUnresolved(host, port))
    }
}

private class ConfigProxySelector(
    private val proxy: Proxy,
    nonProxyHosts: List<String>
) : ProxySelector() {
    private val nonProxyHostMatcher = NonProxyHostMatcher(nonProxyHosts)

    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host ?: return listOf(proxy)
        return if (nonProxyHostMatcher.matches(host)) listOf(Proxy.NO_PROXY) else listOf(proxy)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}

internal class NonProxyHostMatcher(nonProxyHosts: List<String>) {
    private val patterns = nonProxyHosts.mapNotNull { normalizeNoProxyHostToken(it) }

    fun matches(host: String): Boolean {
        val normalizedHost = normalizeNoProxyHostToken(host) ?: return false
        return patterns.any { pattern ->
            when {
                pattern == "*" -> true
                pattern.startsWith("*.") -> normalizedHost.endsWith(".${pattern.removePrefix("*.")}")
                pattern.startsWith(".") -> {
                    val domain = pattern.removePrefix(".")
                    normalizedHost == domain || normalizedHost.endsWith(".$domain")
                }
                else -> normalizedHost == pattern || normalizedHost.endsWith(".$pattern")
            }
        }
    }
}

private fun normalizeNoProxyHostToken(value: String): String? {
    var token = value.trim().lowercase()
    if (token.isBlank()) return null
    if (token == "*") return token

    if (token.startsWith("[")) {
        val bracketEnd = token.indexOf(']')
        if (bracketEnd > 0) return token.substring(1, bracketEnd)
    }

    val onlyColon = token.indexOf(':') == token.lastIndexOf(':')
    if (onlyColon) {
        val colonIndex = token.lastIndexOf(':')
        val port = token.substring(colonIndex + 1)
        if (colonIndex > 0 && port.isNotBlank() && port.all { it.isDigit() }) {
            token = token.substring(0, colonIndex)
        }
    }

    token = token.trimEnd('.')
    return token.ifBlank { null }
}
