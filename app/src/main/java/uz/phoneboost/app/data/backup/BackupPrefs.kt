package uz.phoneboost.app.data.backup

import android.content.Context

/** Avtomatik zaxira yoqilgan/o'chirilganini saqlaydi. Sukut bo'yicha YOQILGAN. Bu fayl zaxiraga kirmaydi. */
class BackupPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var autoBackupEnabled: Boolean
        get() = p.getBoolean(KEY, true)
        set(v) = p.edit().putBoolean(KEY, v).apply()

    companion object {
        const val NAME = "backup_prefs"
        private const val KEY = "auto_backup"
    }
}
