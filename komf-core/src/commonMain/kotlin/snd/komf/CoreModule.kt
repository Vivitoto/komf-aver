package snd.komf

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cookies.HttpCookies
import org.jetbrains.exposed.v1.jdbc.Database
import snd.komf.flaresolverr.DisabledFlareSolverr
import snd.komf.flaresolverr.FlareSolverr
import snd.komf.flaresolverr.FlareSolverrConfig
import snd.komf.flaresolverr.KtorFlareSolverrClient
import snd.komf.ktor.komfUserAgent
import snd.komf.providers.MetadataProvidersConfig
import snd.komf.providers.ProvidersModule
import snd.komf.providers.mangabaka.db.MangaBakaDbDownloader
import snd.komf.providers.mangabaka.db.MangaBakaDbMetadata
import kotlin.io.path.Path
import kotlin.io.path.notExists

private val logger = KotlinLogging.logger {}

class CoreModule(
    private val config: MetadataProvidersConfig,
    ktor: HttpClient,
    onStateRefresh: suspend () -> Unit,
    private val flareSolverrConfig: FlareSolverrConfig = FlareSolverrConfig(),
) {
    private val baseHttpClient = ktor.config {
        expectSuccess = true
        install(HttpCookies.Companion)
        install(UserAgent) { agent = komfUserAgent }

    }

    private val flareSolverr: FlareSolverr = if (flareSolverrConfig.enabled) {
        if (flareSolverrConfig.url.isNullOrBlank()) {
            logger.warn { "FlareSolverr is enabled but no URL is configured; fallback disabled" }
            DisabledFlareSolverr
        } else {
            KtorFlareSolverrClient(baseHttpClient, flareSolverrConfig)
        }
    } else {
        DisabledFlareSolverr
    }

    private val mangaBakaDir = Path(config.mangabakaDatabaseDir)
    private val mangaBakaDatabaseFile = mangaBakaDir.resolve("mangabaka.sqlite")
    val mangaBakaDbMetadata = MangaBakaDbMetadata(
        mangaBakaDir.resolve("timestamp"),
        mangaBakaDir.resolve("checksum.sha1")
    )
    val mangaBakaDatabaseDownloader = MangaBakaDbDownloader(
        baseHttpClient,
        databaseArchive = mangaBakaDir.resolve("mangabaka.tar.gz"),
        databaseFile = mangaBakaDatabaseFile,
        dbMetadata = mangaBakaDbMetadata,
        onStateRefresh = onStateRefresh
    )

    val mangaBakaDatabase =
        if (mangaBakaDatabaseFile.notExists()) null
        else Database.connect("jdbc:sqlite:$mangaBakaDatabaseFile")

    val metadataProviders = ProvidersModule(
        config = config,
        baseHttpClient = baseHttpClient,
        mangaBakaDatabase = mangaBakaDatabase,
        flareSolverr = flareSolverr,
    ).getMetadataProviders()
}
