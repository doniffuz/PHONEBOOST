package uz.phoneboost.app.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

enum class ScaleKind(val settingKey: String) {
    WINDOW(Settings.Global.WINDOW_ANIMATION_SCALE),
    TRANSITION(Settings.Global.TRANSITION_ANIMATION_SCALE),
    ANIMATOR(Settings.Global.ANIMATOR_DURATION_SCALE)
}

/** Tartib UI dagi tartib: 0x, 0.5x, 1x, 1.5x, 2x. */
enum class ScaleOption(val value: Float) { OFF(0f), HALF(0.5f), NORMAL(1f), SLOW(1.5f), SLOWER(2f) }

/**
 * Android animatsiya sozlamalari.
 * O‘qish: ruxsat kerak emas. Yozish: faqat foydalanuvchi ADB orqali WRITE_SECURE_SETTINGS bergan bo‘lsa.
 * Ruxsat berilmagan bo‘lsa hech narsa o‘zgartirilmaydi.
 */
object AnimationScales {
    private const val PREFS = "animation_backup"

    /** Null = o‘qib bo‘lmadi. */
    fun read(context: Context, kind: ScaleKind): Float? = runCatching {
        val v = Settings.Global.getFloat(context.contentResolver, kind.settingKey, -1f)
        if (v < 0f) 1f else v // sozlama yozilmagan bo‘lsa Android standarti 1x
    }.getOrNull()

    /** Aniq variantga to‘g‘ri kelsa o‘shani, aks holda null (maxsus qiymat). */
    fun matchOption(value: Float?): ScaleOption? {
        if (value == null) return null
        return ScaleOption.entries.firstOrNull { kotlin.math.abs(it.value - value) < 0.01f }
    }

    /** Uchala qiymat bir xil variantda bo‘lsa shuni qaytaradi. */
    fun commonOption(values: Map<ScaleKind, Float?>): ScaleOption? {
        val options = ScaleKind.entries.map { matchOption(values[it]) }
        val first = options.firstOrNull() ?: return null
        return if (options.all { it == first }) first else null
    }

    fun developerOptionsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }.getOrDefault(false)

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun hasBackup(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(ScaleKind.WINDOW.name)

    /** Tanlangan variantni uchala qiymatga yozadi. Asl qiymatlar birinchi marta saqlanadi. */
    fun apply(context: Context, option: ScaleOption): Boolean {
        if (!canWrite(context)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!hasBackup(context)) {
            val editor = prefs.edit()
            ScaleKind.entries.forEach { editor.putFloat(it.name, read(context, it) ?: 1f) }
            editor.apply()
        }
        return ScaleKind.entries.map { write(context, it, option.value) }.all { it }
    }

    /** Saqlangan asl qiymatlarni qaytaradi. */
    fun restore(context: Context): Boolean {
        if (!canWrite(context) || !hasBackup(context)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ok = ScaleKind.entries.map { write(context, it, prefs.getFloat(it.name, 1f)) }.all { it }
        if (ok) prefs.edit().clear().apply()
        return ok
    }

    private fun write(context: Context, kind: ScaleKind, value: Float): Boolean =
        runCatching { Settings.Global.putFloat(context.contentResolver, kind.settingKey, value) }.getOrDefault(false)
}
