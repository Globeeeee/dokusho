package eu.kanade.tachiyomi.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.core.metro.IsDebugBuild
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class NetworkPreferences(
    preferenceStore: PreferenceStore,
    @IsDebugBuild isDebugBuild: Boolean,
) {

    val verboseLogging: Preference<Boolean> = preferenceStore.getBoolean(
        "verbose_logging",
        isDebugBuild,
    )

    val dohProvider: Preference<Int> = preferenceStore.getInt("doh_provider", -1)

    val vpnEnabled: Preference<Boolean> = preferenceStore.getBoolean("dokusho_vpn_enabled", false)

    val vpnType: Preference<Int> = preferenceStore.getInt("dokusho_vpn_type", VPN_TYPE_SOCKS)

    val vpnHost: Preference<String> = preferenceStore.getString("dokusho_vpn_host", "")

    val vpnPort: Preference<String> = preferenceStore.getString("dokusho_vpn_port", "")

    val vpnUsername: Preference<String> = preferenceStore.getString("dokusho_vpn_username", "")

    // Stored in the app's private preferences, never logged or included in backups.
    val vpnPassword: Preference<String> = preferenceStore.getString(Preference.privateKey("dokusho_vpn_password"), "")

    val defaultUserAgent: Preference<String> = preferenceStore.getString(
        "default_user_agent",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36",
    )
}
