package uz.phoneboost.app.core.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import uz.phoneboost.app.domain.SettingsAction

/** Android Settings sahifasini ochadi. Ochib bo‘lmasa false qaytaradi (crash bo‘lmaydi). */
fun Context.openSettings(action: SettingsAction): Boolean {
    val intentAction = when (action) {
        SettingsAction.STORAGE -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        SettingsAction.APPS -> Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS
        SettingsAction.NONE -> return false
    }
    return launchSettings(intentAction) || launchSettings(Settings.ACTION_SETTINGS)
}

private fun Context.launchSettings(intentAction: String): Boolean = try {
    startActivity(Intent(intentAction))
    true
} catch (e: Exception) {
    false
}
