package snd.komf.app

import snd.komf.app.config.resolveFlareSolverrConfig
import snd.komf.flaresolverr.FlareSolverrConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlareSolverrConfigTest {

    @Test
    fun environmentOverridesFlareSolverrConfig() {
        val env = mapOf(
            "KOMF_FLARESOLVERR_ENABLED" to "true",
            "KOMF_FLARESOLVERR_URL" to "http://flaresolverr:8191",
            "KOMF_FLARESOLVERR_TIMEOUT_SECONDS" to "45",
            "KOMF_FLARESOLVERR_MAX_TIMEOUT" to "120000",
            "KOMF_FLARESOLVERR_SESSION" to "komf",
        )

        val config = resolveFlareSolverrConfig(
            FlareSolverrConfig(
                enabled = false,
                url = "http://old:8191",
                timeoutSeconds = 10,
                maxTimeout = 10_000,
                session = "old",
            ),
            getenv = env::get,
        )

        assertTrue(config.enabled)
        assertEquals("http://flaresolverr:8191", config.url)
        assertEquals(45L, config.timeoutSeconds)
        assertEquals(120_000L, config.maxTimeout)
        assertEquals("komf", config.session)
    }

    @Test
    fun maxTimeoutMillisecondsAliasIsSupported() {
        val config = resolveFlareSolverrConfig(
            FlareSolverrConfig(),
            getenv = mapOf("KOMF_FLARESOLVERR_MAX_TIMEOUT_MS" to "90000")::get,
        )

        assertEquals(90_000L, config.maxTimeout)
        assertEquals(90_000L, config.maxTimeoutMillis())
    }
}
