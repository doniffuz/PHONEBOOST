package uz.phoneboost.app.data.automation

import android.content.Context

/** Avtomatlashtirish sozlamalari. Hammasi sukut bo‘yicha O‘CHIQ: faqat foydalanuvchi yoqsa ishlaydi. */
class AutomationPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("automation", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    var alerts: Boolean
        get() = p.getBoolean("alerts", false)
        set(v) = p.edit().putBoolean("alerts", v).apply()

    var lastRunMillis: Long
        get() = p.getLong("last_run", 0L)
        set(v) = p.edit().putLong("last_run", v).apply()

    fun lastAlert(kind: String): Long = p.getLong("alert_$kind", 0L)
    fun markAlert(kind: String) = p.edit().putLong("alert_$kind", System.currentTimeMillis()).apply()
}
