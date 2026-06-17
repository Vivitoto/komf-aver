package snd.komf.app

import snd.komf.app.config.parseProxyUrlCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxySupportTest {

    @Test
    fun parsesAndDecodesProxyUrlCredentials() {
        val credentials = parseProxyUrlCredentials("http://user%40name:p%2Bss%3Aword@proxy.example:7890")

        assertEquals("user@name", credentials?.username)
        assertEquals("p+ss:word", credentials?.password)
    }

    @Test
    fun matchesStandardNoProxyHostPatterns() {
        val matcher = NonProxyHostMatcher(
            listOf(
                "exact.example",
                "*.wild.example",
                ".dot.example",
                "plain.example",
                "host.example:8080",
                "[::1]:9090",
            )
        )

        assertTrue(matcher.matches("exact.example"))
        assertTrue(matcher.matches("api.wild.example"))
        assertFalse(matcher.matches("wild.example"))
        assertTrue(matcher.matches("dot.example"))
        assertTrue(matcher.matches("api.dot.example"))
        assertTrue(matcher.matches("plain.example"))
        assertTrue(matcher.matches("api.plain.example"))
        assertFalse(matcher.matches("notplain.example"))
        assertTrue(matcher.matches("host.example"))
        assertTrue(matcher.matches("[::1]"))
        assertTrue(matcher.matches("::1"))
    }

    @Test
    fun wildcardNoProxyHostMatchesEverything() {
        assertTrue(NonProxyHostMatcher(listOf("*")).matches("anything.example"))
    }
}
