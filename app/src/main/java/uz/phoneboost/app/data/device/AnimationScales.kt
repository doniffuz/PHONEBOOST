package uz.phoneboost.app.data.device

import android.content.Context
import android.provider.Settings

enum class ScaleKind(val settingKey: String) {
    WINDOW(Settings.Global.WINDOW_ANIMATION_SCALE),
    TRANSITION(Settings.Global.TRANSITION_ANIMATION_SCALE),
    ANIMATOR(Settings.Global.ANIMATOR_DURATION_SCALE)
}

/** Tartib UI dagi tartib: 0x, 0.5x, 1x, 1.5x, 2x. */
enum class ScaleOption(val value: Float) { OFF(0f), HALF(0.5f), NORMAL(1f), SLOW(1.5f), SLOWER(2f) }

/**
 * Android animatsiya sozlamalarini FAQAT O‘QIYDI (ruxsat kerak emas).
 * O‘zgartirish ilovada yo‘q: bu sozlamalarni Android oddiy ilovaga yozdirmaydi (WRITE_SECURE_SETTINGS maxsus ruxsati
 * kerak, u Google Play siyosatiga zid bo‘lishi mumkin va ilovadan olib tashlangan). Foydalanuvchi Developer options orqali o‘zi o‘zgartiradi.
 */
object AnimationScales {
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

    fun developerOptionsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }.getOrDefault(false)
}
