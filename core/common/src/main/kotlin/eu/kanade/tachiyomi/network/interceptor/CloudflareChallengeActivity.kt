package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * Shows a Cloudflare check that needs a person (the "Verify you are human" box) when the hidden
 * WebView in [CloudflareInterceptor] gives up. Closes itself as soon as the site issues a new
 * clearance cookie, so the waiting request carries on without the user retrying.
 */
class CloudflareChallengeActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var url: String
    private var previousClearance: String? = null

    private val checkClearance: Runnable = Runnable { pollPage() }

    private fun pollPage() {
        if (isFinishing || !::webView.isInitialized) return
        val clearance = CookieManager.getInstance().getCookie(url)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$CLEARANCE_COOKIE=") }
            ?.substringAfter('=')
        if (!clearance.isNullOrBlank() && clearance != previousClearance) {
            markSolved()
            return
        }
        webView.evaluateJavascript(DOCUMENT_HTML) { raw ->
            if (isFinishing) return@evaluateJavascript
            val html = decodeJsString(raw)
            val blocked = isCloudflareBlockPage(html)
            val challenge = isCloudflareChallengePage(html) || html.isBlank()
            if (!blocked && !challenge && html.length > MIN_PAGE_LENGTH) {
                markSolved()
            } else {
                handler.postDelayed(checkClearance, POLL_MS)
            }
        }
    }

    private fun markSolved() {
        CookieManager.getInstance().flush()
        solvedAt = System.currentTimeMillis()
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        previousClearance = intent.getStringExtra(EXTRA_PREVIOUS_CLEARANCE)

        val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
        val hint = TextView(this).apply {
            text = stringResource(MR.strings.cloudflare_challenge_hint)
            setPadding(padding, padding, padding, padding)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1F2A44"))
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            intent.getStringExtra(EXTRA_USER_AGENT)?.let { settings.userAgentString = it }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                fitsSystemWindows = true
                addView(
                    hint,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
                addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
        title = stringResource(MR.strings.cloudflare_challenge_title)

        @Suppress("UNCHECKED_CAST")
        val headers = (intent.getSerializableExtra(EXTRA_HEADERS) as? HashMap<String, String>).orEmpty()
        webView.loadUrl(url, headers)
        handler.postDelayed(checkClearance, POLL_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkClearance)
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        // Lets the waiting request stop at once when the user backs out instead of solving.
        closedAt = System.currentTimeMillis()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_USER_AGENT = "user_agent"
        const val EXTRA_HEADERS = "headers"
        const val EXTRA_PREVIOUS_CLEARANCE = "previous_clearance"
        private const val CLEARANCE_COOKIE = "cf_clearance"
        private const val POLL_MS = 500L

        /** When the page last closed, read by the interceptor while it waits. */
        @Volatile
        var closedAt: Long = 0L
            private set

        /** When the WebView actually showed the site, even if no new clearance cookie appeared. */
        @Volatile
        var solvedAt: Long = 0L
            private set

        private const val MIN_PAGE_LENGTH = 512
    }
}
