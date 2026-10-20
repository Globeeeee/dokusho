package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.network.VPN_TYPE_HTTP
import eu.kanade.tachiyomi.network.VPN_TYPE_SOCKS
import eu.kanade.tachiyomi.network.checkVpn
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/** Routes Dokusho's own traffic through a proxy server, so sites blocked on the network still load. */
object SettingsVpnScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_vpn

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val prefs = remember { context.appGraph.networkPreferences }
        val enabled by prefs.vpnEnabled.collectAsState()
        val password by prefs.vpnPassword.collectAsState()
        val restart: suspend (Any) -> Boolean = {
            context.toast(MR.strings.requires_app_restart)
            true
        }

        return listOf(
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.pref_vpn_info)),
            Preference.PreferenceItem.SwitchPreference(
                preference = prefs.vpnEnabled,
                title = stringResource(MR.strings.pref_vpn_enabled),
                subtitle = stringResource(MR.strings.pref_vpn_enabled_summary),
                onValueChanged = restart,
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_vpn_server),
                visible = enabled,
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.vpnType,
                        entries = mapOf(
                            VPN_TYPE_SOCKS to "SOCKS5",
                            VPN_TYPE_HTTP to "HTTP",
                        ),
                        title = stringResource(MR.strings.pref_vpn_type),
                        onValueChanged = restart,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.vpnHost,
                        title = stringResource(MR.strings.pref_vpn_host),
                        onValueChanged = restart,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.vpnPort,
                        title = stringResource(MR.strings.pref_vpn_port),
                        onValueChanged = {
                            val valid = it.trim().toIntOrNull()?.let { port -> port in 1..65535 } == true
                            if (valid) restart(it) else context.toast(MR.strings.pref_vpn_port_invalid)
                            valid
                        },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.vpnUsername,
                        title = stringResource(MR.strings.pref_vpn_username),
                        onValueChanged = restart,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.vpnPassword,
                        title = stringResource(MR.strings.pref_vpn_password),
                        subtitle = if (password.isEmpty()) null else "••••••••",
                        onValueChanged = restart,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_vpn_check),
                        subtitle = stringResource(MR.strings.pref_vpn_check_summary),
                        onClick = {
                            scope.launch {
                                val message = withContext(Dispatchers.IO) {
                                    runCatching { checkVpn(prefs) }.fold(
                                        onSuccess = { ip ->
                                            if (ip == null) {
                                                context.stringResource(MR.strings.pref_vpn_check_incomplete)
                                            } else {
                                                context.stringResource(MR.strings.pref_vpn_check_ok, ip)
                                            }
                                        },
                                        onFailure = { context.stringResource(MR.strings.pref_vpn_check_failed) },
                                    )
                                }
                                context.toast(message)
                            }
                        },
                    ),
                ),
            ),
        )
    }
}
