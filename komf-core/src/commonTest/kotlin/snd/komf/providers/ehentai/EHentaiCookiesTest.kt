package snd.komf.providers.ehentai

import snd.komf.providers.EHentaiConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EHentaiCookiesTest {

    @Test
    fun parsesBrowserCookieHeader() {
        val cookies = parseEHentaiCookieHeader(
            "Cookie: ipb_member_id=member; ipb_pass_hash=hash; igneous=igneous; malformed; a=b=c; empty=; =ignored"
        )

        assertEquals("member", cookies["ipb_member_id"])
        assertEquals("hash", cookies["ipb_pass_hash"])
        assertEquals("igneous", cookies["igneous"])
        assertEquals("b=c", cookies["a"])
        assertFalse(cookies.containsKey(""))
        assertFalse(cookies.containsKey("malformed"))
        assertFalse(cookies.containsKey("empty"))
    }

    @Test
    fun explicitCookieMapOverridesCookieHeader() {
        val cookies = EHentaiConfig(
            cookieHeader = "ipb_member_id=from-header; ipb_pass_hash=from-header; igneous=from-header",
            cookies = mapOf(
                "ipb_member_id" to "from-map",
                "ipb_pass_hash" to "from-map",
                "igneous" to "",
            )
        ).effectiveCookies()

        assertEquals("from-map", cookies["ipb_member_id"])
        assertEquals("from-map", cookies["ipb_pass_hash"])
        assertFalse(cookies.containsKey("igneous"))
    }
}
