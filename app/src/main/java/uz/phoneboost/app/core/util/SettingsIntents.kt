package uz.phoneboost.app.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import uz.phoneboost.app.domain.SettingsAction

/** Intentni xavfsiz ishga tushiradi. Ochib bo‘lmasa false qaytaradi (crash bo‘lmaydi). */
fun Context.startSafely(intent: Intent): Boolean = try {
    if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
} catch (e: Exception) {
    false
}

/** Android Settings sahifasini ochadi. Ochib bo‘lmasa false qaytaradi (crash bo‘lmaydi). */
fun Context.openSettings(action: SettingsAction): Boolean {
    val intentAction = when (action) {
        SettingsAction.STORAGE -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        SettingsAction.APPS -> Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS
        SettingsAction.NONE -> return false
    }
    return startSafely(Intent(intentAction)) || startSafely(Intent(Settings.ACTION_SETTINGS))
}

/** Ilova tafsilotlari (Android Settings). */
fun Context.openAppDetails(packageName: String): Boolean =
    startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) ||
        startSafely(Intent(Settings.ACTION_SETTINGS))

/** O‘chirish faqat Android tizimining tasdiqlash oynasi orqali amalga oshadi. */
fun Context.requestUninstall(packageName: String): Boolean =
    startSafely(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))

fun Context.openDeveloperOptions(): Boolean =
    startSafely(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) ||
        startSafely(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) ||
        startSafely(Intent(Settings.ACTION_SETTINGS))

fun Context.openAboutPhone(): Boolean =
    startSafely(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) || startSafely(Intent(Settings.ACTION_SETTINGS))

fun Context.openBatteryOptimizationSettings(): Boolean =
    startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
        startSafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) ||
        startSafely(Intent(Settings.ACTION_SETTINGS))

fun Context.openOwnNotificationSettings(): Boolean =
    startSafely(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    ) || openAppDetails(packageName)

fun Context.openUrl(url: String): Boolean =
    url.isNotBlank() && startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

fun Context.openEmail(address: String): Boolean =
    address.isNotBlank() && startSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))

/** Play Store (yoki boshqa market) dagi ilova sahifasi; bo‘lmasa veb-havola. */
fun Context.openRateApp(): Boolean =
    startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) ||
        startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))

fun Context.launchApp(packageName: String): Boolean {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    return startSafely(intent)
}
