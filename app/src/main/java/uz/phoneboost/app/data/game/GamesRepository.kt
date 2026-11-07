package uz.phoneboost.app.data.game

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File

data class InstalledGame(
    val packageName: String,
    val label: String,
    val apkSizeBytes: Long?,
    val icon: Bitmap?
)

/**
 * O‘yinlarni universal aniqlaydi: Android o‘zi “o‘yin” deb belgilagan (CATEGORY_GAME) launcher ilovalari.
 * Hech bir konkret o‘yinga bog‘lanmagan. Funksiya bloklovchi: IO oqimidan chaqiring.
 */
class GamesRepository(context: Context) {
    private val ctx = context.applicationContext

    @Suppress("DEPRECATION")
    fun load(): List<InstalledGame> = runCatching {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != ctx.packageName }
            .filter { ai ->
                ai.category == ApplicationInfo.CATEGORY_GAME ||
                    (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
            .distinctBy { it.packageName }
            .map { ai ->
                InstalledGame(
                    packageName = ai.packageName,
                    label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName),
                    apkSizeBytes = runCatching { File(ai.sourceDir).length() }.getOrNull()?.takeIf { it > 0 },
                    icon = runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96) }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}
