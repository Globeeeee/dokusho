package eu.kanade.tachiyomi.network

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy

/**
 * The in-app "VPN": routes Dokusho's own traffic (OkHttp and WebView) through a proxy server the
 * user supplies. Nothing else on the phone is affected. Read once per app start.
 */
internal data class VpnConfig(
    val type: Int,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
) {
    val proxyType get() = if (type == VPN_TYPE_SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
    val hasLogin get() = username.isNotBlank()

    companion object {
        fun from(preferences: NetworkPreferences): VpnConfig? {
            if (!preferences.vpnEnabled.get()) return null
            val host = preferences.vpnHost.get().trim()
            val port = preferences.vpnPort.get().trim().toIntOrNull()
            if (host.isEmpty() || port == null || port !in 1..65535) return null
            return VpnConfig(
                type = preferences.vpnType.get(),
                host = host,
                port = port,
                username = preferences.vpnUsername.get().trim(),
                password = preferences.vpnPassword.get(),
            )
        }
    }
}

internal fun OkHttpClient.Builder.vpn(config: VpnConfig): OkHttpClient.Builder {
    proxy(Proxy(config.proxyType, InetSocketAddress.createUnresolved(config.host, config.port)))
    if (!config.hasLogin) return this
    if (config.proxyType == Proxy.Type.HTTP) {
        proxyAuthenticator(
            Authenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
                response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
                    .build()
            },
        )
    } else {
        // SOCKS5 login goes through the JVM authenticator; answer only for our proxy host.
        java.net.Authenticator.setDefault(
            object : java.net.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    if (!requestingHost.equals(config.host, ignoreCase = true)) return null
                    return PasswordAuthentication(config.username, config.password.toCharArray())
                }
            },
        )
    }
    return this
}

/** Sends WebView traffic (Cloudflare checks, site logins) through the same proxy, where supported. */
internal fun applyWebViewVpn(config: VpnConfig?) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
    val controller = ProxyController.getInstance()
    val direct = Runnable {}
    if (config == null) {
        controller.clearProxyOverride({ it.run() }, direct)
        return
    }
    val scheme = if (config.proxyType == Proxy.Type.SOCKS) "socks" else "http"
    val proxyConfig = ProxyConfig.Builder()
        .addProxyRule("$scheme://${config.host}:${config.port}")
        .build()
    controller.setProxyOverride(proxyConfig, { it.run() }, direct)
}

const val VPN_TYPE_HTTP = 0
const val VPN_TYPE_SOCKS = 1

/**
 * Connects through the saved VPN settings without restarting the app and returns the IP address
 * sites see, or null when the VPN is off or incomplete.
 */
suspend fun checkVpn(preferences: NetworkPreferences): String? {
    val config = VpnConfig.from(preferences) ?: return null
    val client = OkHttpClient.Builder().vpn(config).build()
    val trace = client.newCall(GET("https://www.cloudflare.com/cdn-cgi/trace")).awaitSuccess()
        .use { it.body.string() }
    return trace.lineSequence().firstOrNull { it.startsWith("ip=") }?.removePrefix("ip=") ?: "?"
}
