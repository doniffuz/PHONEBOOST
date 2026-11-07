package uz.phoneboost.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.phoneboost.app.domain.model.AppEntry
import java.io.File

/**
 * Launcher’da ko‘rinadigan ilovalar. QUERY_ALL_PACKAGES kerak emas (manifestdagi <queries> yetarli).
 * Ruxsatlar ro‘yxati va APK hajmi — Android bergan real ma’lumot. Ilova ma’lumotlari hajmi (Usage Access talab qiladi) olinmaydi.
 */
class AppsRepository(private val context: Context) {

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcher, 0)
            }
        }.getOrDefault(emptyList())

        resolved
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .mapNotNull { pkg -> runCatching { buildEntry(pm, pkg) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    private fun buildEntry(pm: PackageManager, pkg: String): AppEntry? {
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }
        val appInfo: ApplicationInfo = info.applicationInfo ?: return null

        val requested = info.requestedPermissions
        val flags = info.requestedPermissionsFlags
        val granted = HashSet<String>()
        if (requested != null && flags != null) {
            for (i in requested.indices) {
                if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    granted += requested[i]
                }
            }
        }

        val size = runCatching {
            var total = File(appInfo.sourceDir).length()
            appInfo.splitSourceDirs?.forEach { total += File(it).length() }
            total.takeIf { it > 0 }
        }.getOrNull()

        return AppEntry(
            packageName = pkg,
            label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg),
            isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            apkSizeBytes = size,
            versionName = info.versionName,
            installedAtMillis = info.firstInstallTime.takeIf { it > 0 },
            grantedPermissions = granted
        )
    }
}
