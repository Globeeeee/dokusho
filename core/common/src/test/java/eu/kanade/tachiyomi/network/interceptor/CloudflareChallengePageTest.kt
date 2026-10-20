package eu.kanade.tachiyomi.network.interceptor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudflareChallengePageTest {

    @Test
    fun `challenge page is detected`() {
        val html = """
            <html><head><title>Just a moment...</title></head><body>
            <script>window._cf_chl_opt = {cType: 'managed'};</script>
            <script src="/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1"></script>
            </body></html>
        """.trimIndent()
        assertTrue(isCloudflareChallengePage(html))
    }

    @Test
    fun `real page with the injected challenge-platform script is not a challenge`() {
        val html = """
            <html><head><title>Shadow Slave - Novel Phoenix</title></head><body>
            <div id="content"><p>Chapter text</p></div>
            <script>(function(){var a=document.createElement('script');
            a.src='/cdn-cgi/challenge-platform/scripts/jsd/main.js';})();</script>
            </body></html>
        """.trimIndent()
        assertFalse(isCloudflareChallengePage(html))
    }
}
