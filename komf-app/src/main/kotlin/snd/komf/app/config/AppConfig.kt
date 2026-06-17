package snd.komf.app.config

import kotlinx.serialization.Serializable
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import snd.komf.mediaserver.config.DatabaseConfig
import snd.komf.mediaserver.config.KavitaConfig
import snd.komf.mediaserver.config.KomgaConfig
import snd.komf.notifications.NotificationsConfig
import snd.komf.providers.MetadataProvidersConfig

@Serializable
data class AppConfig(
    val komga: KomgaConfig = KomgaConfig(),
    val kavita: KavitaConfig = KavitaConfig(),
    val database: DatabaseConfig = DatabaseConfig(),
    val metadataProviders: MetadataProvidersConfig = MetadataProvidersConfig(),
    val notifications: NotificationsConfig = NotificationsConfig(),
    val proxy: ProxyConfig = ProxyConfig(),
    val server: ServerConfig = ServerConfig(),
    val logLevel: String = "INFO",
    val httpLogLevel: HttpLoggingInterceptor.Level = BASIC
)

@Serializable
data class ProxyConfig(
    val enabled: Boolean = false,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val nonProxyHosts: List<String> = listOf("localhost", "127.0.0.1", "komga", "kavita")
)

@Serializable
data class ServerConfig(
    val port: Int = 8085
)
