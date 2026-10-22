package eu.kanade.domain.base

import android.content.Context
import eu.kanade.domain.base.BasePreferences.ExtensionInstaller
import eu.kanade.tachiyomi.util.system.isShizukuInstalled
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class ExtensionInstallerPreference(
    private val context: Context,
    preferenceStore: PreferenceStore,
) : Preference<ExtensionInstaller> {

    private val basePref = preferenceStore.getEnum(key(), defaultValue())

    override fun key() = "extension_installer"

    // Dokusho ships without REQUEST_INSTALL_PACKAGES, so only installers that don't need it are offered
    val entries get() = listOf(ExtensionInstaller.PRIVATE, ExtensionInstaller.SHIZUKU)

    override fun defaultValue() = ExtensionInstaller.PRIVATE

    private fun check(value: ExtensionInstaller): ExtensionInstaller {
        when (value) {
            ExtensionInstaller.LEGACY, ExtensionInstaller.PACKAGEINSTALLER -> return defaultValue()
            ExtensionInstaller.SHIZUKU -> {
                if (!context.isShizukuInstalled) return defaultValue()
            }
            else -> {}
        }
        return value
    }

    override fun get(): ExtensionInstaller {
        val value = basePref.get()
        val checkedValue = check(value)
        if (value != checkedValue) {
            basePref.set(checkedValue)
        }
        return checkedValue
    }

    override fun set(value: ExtensionInstaller) {
        basePref.set(check(value))
    }

    override fun isSet() = basePref.isSet()

    override fun delete() = basePref.delete()

    override fun changes() = basePref.changes()

    override fun stateIn(scope: CoroutineScope) = basePref.stateIn(scope)
}
