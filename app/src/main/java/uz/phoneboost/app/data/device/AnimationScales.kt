package uz.phoneboost.app.data.device

import android.content.Context
import android.provider.Settings

enum class ScaleKind(val settingKey: String) {
    WINDOW(Settings.Global.WINDOW_ANIMATION_SCALE),
    TRANSITION(Settings.Global.TRANSITION_ANIMATION_SCALE),
    ANIMATOR(Settings.Global.ANIMATOR_DURATION_SCALE)
}

enum class ScaleOption(val value: Float) { NORMAL(1f), HALF(0.5f), OFF(0f) }

/** Android animatsiya sozlamalarining HAQIQIY joriy qiymatini o‘qiydi (o‘qish uchun ruxsat kerak emas). */
object AnimationScales {

    /** Null = o‘qib bo‘lmadi. */
    fun read(context: Context, kind: ScaleKind): Float? = runCatching {
        val v = Settings.Global.getFloat(context.contentResolver, kind.settingKey, -1f)
        if (v < 0f) 1f else v // sozlama yozilmagan bo‘lsa Android standarti 1x
    }.getOrNull()

    /** Aniq 1x / 0.5x / Off ga to‘g‘ri kelsa o‘shani, aks holda null (maxsus qiymat). */
    fun matchOption(value: Float?): ScaleOption? {
        if (value == null) return null
        return ScaleOption.entries.firstOrNull { kotlin.math.abs(it.value - value) < 0.01f }
    }

    fun developerOptionsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }.getOrDefault(false)
}
