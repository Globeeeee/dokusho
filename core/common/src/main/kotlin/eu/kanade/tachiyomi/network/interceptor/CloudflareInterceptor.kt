package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toWebViewHeaders
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import logcat.LogPriority
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    scope: CoroutineScope,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, scope, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    // Requests to a host share its lock while a challenge is solved under it alone, so concurrent requests wait for
    // one WebView instead of each opening their own
    private val hostLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    private val listenerScript = """
        addEventListener("message", ({data}) => {
            if (data?.source === "cloudflare-challenge") {
                mihon?.postMessage(data.event);
            }
        })
    """.trimIndent()

    override fun shouldIntercept(response: Response): Boolean {
        // Check if Cloudflare anti-bot is on
        // Checking the cf-mitigated header is the official way to detect a Cloudflare challenge:
        // https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
        return response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val lock = hostLocks.computeIfAbsent(request.url.host) { ReentrantReadWriteLock() }

        val (response, clearance) = lock.read {
            val response = chain.proceed(request)
            if (!shouldIntercept(response)) return response
            response to clearance(request.url)
        }

        // write releases the read hold of a request made from within another to the same host, like an extension
        // refreshing a token, which would otherwise wait on itself
        val bypass = lock.write {
            // A clearance earned while this request waited is worth one OkHttp retry. Cloudflare
            // binds that cookie to the client that solved it, so a retry that is challenged again
            // (the tag) is answered with the page the WebView actually loaded.
            val retried = request.tag(ClearanceRetry::class.java) != null
            if (!retried && isCleared(request.url, clearance)) {
                response.close()
                Bypass.RetryWithCookie
            } else {
                if (!prepareWebView()) return response
                solve(request, response, clearance)
            }
        }

        return when (bypass) {
            Bypass.RetryWithCookie -> chain.proceed(retriedRequest(request))
            Bypass.Cookie -> {
                if (request.tag(ClearanceRetry::class.java) != null) {
                    throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure))
                }
                chain.proceed(retriedRequest(request))
            }
            is Bypass.Document -> documentResponse(request, bypass.html)
        }
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        return when (val bypass = solve(request, response, clearance(request.url))) {
            is Bypass.Document -> documentResponse(request, bypass.html)
            Bypass.Cookie, Bypass.RetryWithCookie -> chain.proceed(request)
        }
    }

    private fun clearance(url: HttpUrl): String? {
        return cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }?.value
    }

    private fun isCleared(url: HttpUrl, previousClearance: String?): Boolean {
        val clearance = clearance(url)
        return !clearance.isNullOrBlank() && clearance != previousClearance
    }

    private fun solve(request: Request, response: Response, clearance: String?): Bypass {
        try {
            response.close()
            // Keep cf_clearance. OkHttp is often rejected after the WebView has already solved the
            // check; deleting the cookie here throws away the session that can still load the page.
            val url = challengeUrl(request)
            val headers = request.headers.toWebViewHeaders()
            val first = resolveWithWebView(request, url, headers, allowInteractive = true)
            val attempt = if (first.html == null && !first.bypassed && first.interactive && isAppInForeground()) {
                val opened = solveInteractively(request, url, headers, clearance)
                if (opened) {
                    resolveWithWebView(request, url, headers, allowInteractive = false)
                } else {
                    first
                }
            } else {
                first
            }
            if (!attempt.html.isNullOrBlank()) return Bypass.Document(attempt.html)
            if (attempt.bypassed) return Bypass.Cookie

            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            if (attempt.outdated) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            }
            throw CloudflareBypassException()
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    /**
     * Opens [CloudflareChallengeActivity] for [challengeUrl] with the request's user agent, so the
     * clearance it earns is valid for the request, and waits until a new clearance appears, the
     * user closes the page, or [INTERACTIVE_TIMEOUT_MS] passes.
     */
    private fun solveInteractively(
        request: Request,
        challengeUrl: String,
        headers: Map<String, String>,
        previousClearance: String?,
    ): Boolean {
        val openedAt = System.currentTimeMillis()
        val intent = Intent()
            .setClassName(context, CloudflareChallengeActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CloudflareChallengeActivity.EXTRA_URL, challengeUrl)
            .putExtra(CloudflareChallengeActivity.EXTRA_USER_AGENT, request.header("User-Agent"))
            .putExtra(CloudflareChallengeActivity.EXTRA_HEADERS, HashMap(headers))
            .putExtra(CloudflareChallengeActivity.EXTRA_PREVIOUS_CLEARANCE, previousClearance)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Could not open the Cloudflare check" }
            return false
        }
        while (System.currentTimeMillis() - openedAt < INTERACTIVE_TIMEOUT_MS) {
            if (CloudflareChallengeActivity.solvedAt > openedAt) return true
            if (isCleared(request.url, previousClearance)) return true
            if (CloudflareChallengeActivity.closedAt > openedAt) return isCleared(request.url, previousClearance)
            Thread.sleep(INTERACTIVE_POLL_MS)
        }
        return false
    }

    /**
     * Loads [pageUrl] and waits until the document is the real page. The clearance cookie is not
     * enough on its own: OkHttp's fingerprint is rejected even after this WebView has passed, so
     * the document loaded here is what the request returns.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(
        originalRequest: Request,
        pageUrl: String,
        headers: Map<String, String>,
        allowInteractive: Boolean,
    ): WebViewAttempt {
        // OkHttp doesn't support asynchronous interceptors, so this thread waits for the WebView.
        val latch = CountDownLatch(1)
        val finished = AtomicBoolean(false)

        var webview: WebView? = null
        var listenerScriptHandler: ScriptHandler? = null
        val useIsolatedWorld = WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)

        var challengeInteractive = false
        var cloudflareBypassed = false
        var isWebViewOutdated = false
        var isRenderProcessGone = false
        var documentHtml: String? = null
        var listenerInjected = false

        val origRequestUrl = originalRequest.url.toString()
        val startedAt = System.currentTimeMillis()

        fun finishAttempt() {
            if (finished.compareAndSet(false, true)) latch.countDown()
        }

        executor.execute {
            val view = createWebView(originalRequest)
            webview = view
            // A WebView with no size fails checks that look at the viewport.
            view.layoutForChallenge()

            fun onChallengeEvent(event: String) {
                when (event) {
                    // The challenge cannot be solved non-interactively; hand it to the user below.
                    "interactiveBegin" -> if (allowInteractive) {
                        challengeInteractive = true
                        finishAttempt()
                    }
                    "fail" -> finishAttempt()
                }
            }

            fun inspectDocument() {
                if (finished.get()) return
                view.evaluateJavascript(DOCUMENT_HTML) { raw ->
                    if (finished.get()) return@evaluateJavascript
                    val html = decodeJsString(raw)
                    fun again() {
                        view.postDelayed({ inspectDocument() }, DOCUMENT_POLL_MS)
                    }
                    when {
                        html.isBlank() -> again()
                        isCloudflareBlockPage(html) -> finishAttempt()
                        isCloudflareChallengePage(html) -> {
                            val waited = System.currentTimeMillis() - startedAt
                            if (allowInteractive && waited > INTERACTIVE_FALLBACK_MS && isAppInForeground()) {
                                challengeInteractive = true
                                finishAttempt()
                            } else {
                                if (!useIsolatedWorld && !listenerInjected) {
                                    listenerInjected = true
                                    view.evaluateJavascript(listenerScript, null)
                                }
                                again()
                            }
                        }
                        else -> {
                            documentHtml = html
                            // The WebView got past the check. GET returns this document; other
                            // methods only learned that the cookie is good for a single retry.
                            cloudflareBypassed = true
                            flushCookies()
                            finishAttempt()
                        }
                    }
                }
            }

            if (useIsolatedWorld) {
                // An isolated world keeps the bridge out of the page's reach
                val world = WebViewCompat.getExecutionWorld(view, "mihon")
                val origins = setOf(originalRequest.url.origin())
                WebViewCompat.addWebMessageListener(view, "mihon", origins, world) { _, message, _, isMainFrame, _ ->
                    if (isMainFrame) message.data?.let(::onChallengeEvent)
                }
                listenerScriptHandler = WebViewCompat.addJavaScriptOnEvent(
                    view,
                    listenerScript,
                    WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                    origins,
                    world,
                )
            } else {
                view.addJavascriptInterface(
                    object {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun postMessage(event: String) = onChallengeEvent(event)
                    },
                    "mihon",
                )
            }

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(finishedView: WebView, url: String) {
                    inspectDocument()
                }

                override fun onReceivedHttpError(
                    finishedView: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    // WebView often hides cf-mitigated, so a missing header is not "no challenge".
                    // The document poll decides whether this page is the check or the real site.
                    if (request?.isForMainFrame != true) return
                    if (errorResponse?.responseHeaders?.get("cf-mitigated") == "challenge") {
                        logcat(LogPriority.DEBUG) { "Cloudflare challenge page at $pageUrl" }
                    }
                }

                override fun onRenderProcessGone(finishedView: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // Not handling it kills the app, failing the bypass is enough
                    logcat(LogPriority.ERROR) {
                        "WebView renderer gone during Cloudflare bypass: crashed=${detail.didCrash()}, " +
                            "priority=${detail.rendererPriorityAtExit()}, url=$origRequestUrl"
                    }
                    isRenderProcessGone = true
                    finishAttempt()
                    return true
                }
            }

            view.loadUrl(pageUrl, headers)
        }

        latch.awaitFor30Seconds()
        finished.set(true)

        val destroyed = CountDownLatch(1)
        executor.execute {
            // Only destroy can be called on a WebView whose renderer is gone
            if (!cloudflareBypassed && !isRenderProcessGone) {
                isWebViewOutdated = webview?.isOutdated() == true
            }

            webview?.run {
                if (!isRenderProcessGone) {
                    if (useIsolatedWorld) {
                        WebViewCompat.removeWebMessageListener(
                            this,
                            WebViewCompat.getExecutionWorld(this, "mihon"),
                            "mihon",
                        )
                    } else {
                        removeJavascriptInterface("mihon")
                    }
                    listenerScriptHandler?.remove()
                    stopLoading()
                }
                destroy()
            }
            destroyed.countDown()
        }
        destroyed.await(5, TimeUnit.SECONDS)

        // A GET that rendered the real page is the response. Other methods only need the cookie,
        // which a later request retries once.
        val html = documentHtml?.takeIf { originalRequest.method == "GET" }
        return WebViewAttempt(
            html = html,
            bypassed = cloudflareBypassed || html != null,
            interactive = challengeInteractive && !isRenderProcessGone,
            outdated = isWebViewOutdated,
        )
    }

    private fun challengeUrl(request: Request): String {
        // A WebView can only load a page with GET, and loading the url of another method usually reaches an
        // endpoint that answers GET without a challenge to solve. The clearance covers the whole site, so it's
        // earned on the site's root page instead.
        if (request.method == "GET") return request.url.toString()
        return request.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
    }

    private fun retriedRequest(request: Request): Request {
        return request.newBuilder().tag(ClearanceRetry::class.java, ClearanceRetry()).build()
    }

    private fun documentResponse(request: Request, html: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", "text/html; charset=utf-8")
            .body(html.toResponseBody(HTML_MEDIA_TYPE))
            .build()
    }
}

private class WebViewAttempt(
    val html: String?,
    val bypassed: Boolean,
    val interactive: Boolean,
    val outdated: Boolean,
)

private sealed interface Bypass {
    data class Document(val html: String) : Bypass
    data object Cookie : Bypass
    data object RetryWithCookie : Bypass
}

/** Marks the one OkHttp retry that carries a clearance earned by the WebView. */
private class ClearanceRetry

private fun WebView.layoutForChallenge() {
    val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels.coerceAtLeast(1)
    val height = metrics.heightPixels.coerceAtLeast(1)
    measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    layout(0, 0, width, height)
}

private fun flushCookies() {
    try {
        CookieManager.getInstance().flush()
    } catch (e: Exception) {
        e.logcat(LogPriority.ERROR) { "Could not flush WebView cookies" }
    }
}

internal fun decodeJsString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return try {
        JSONArray("[$value]").getString(0)
    } catch (_: Exception) {
        ""
    }
}

internal fun isCloudflareChallengePage(html: String): Boolean {
    return "window._cf_chl_opt" in html ||
        "cf-browser-verification" in html ||
        "/cdn-cgi/challenge-platform/" in html ||
        "id=\"challenge-form\"" in html ||
        "<title>Just a moment" in html
}

internal fun isCloudflareBlockPage(html: String): Boolean {
    val lower = html.lowercase()
    return ("you have been blocked" in lower || "error 1020" in lower) && "cloudflare" in lower
}

private fun CloudflareInterceptor.isAppInForeground(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
    return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
}

// An origin rule without a port only matches the scheme's default one
private fun HttpUrl.origin(): String {
    return if (port == HttpUrl.defaultPort(scheme)) "$scheme://$host" else "$scheme://$host:$port"
}

private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private const val INTERACTIVE_TIMEOUT_MS = 180_000L
private const val INTERACTIVE_POLL_MS = 500L
private const val DOCUMENT_POLL_MS = 1_000L
private const val INTERACTIVE_FALLBACK_MS = 8_000L
internal const val DOCUMENT_HTML = "document.documentElement.outerHTML"
private val HTML_MEDIA_TYPE = "text/html; charset=utf-8".toMediaType()
private val COOKIE_NAMES = listOf("cf_clearance")

private class CloudflareBypassException : Exception()
