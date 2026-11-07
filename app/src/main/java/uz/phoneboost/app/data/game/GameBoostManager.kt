package uz.phoneboost.app.data.game

import android.content.Context
import androidx.annotation.StringRes
import uz.phoneboost.app.R
import uz.phoneboost.app.data.device.AnimationScales
import uz.phoneboost.app.data.device.ScaleOption

data class BoostAction(@StringRes val title: Int, val done: Boolean, @StringRes val reason: Int? = null)

enum class RestoreOutcome { RESTORED, NOTHING_TO_RESTORE, NO_PERMISSION, FAILED }

/**
 * Game Boost faqat REAL va ruxsat etilgan amalni bajaradi: animatsiya tezligini 0.5x ga tushirish
 * (WRITE_SECURE_SETTINGS bo‘lsa). Asl qiymatlar AnimationScales ichida saqlanadi va Restore bilan qaytariladi.
 * FPS oshirish, boshqa ilovalarni yopish kabi narsalar Androidda mumkin emas — “bajarilmadi” deb ko‘rsatiladi.
 */
object GameBoostManager {
    private const val PREFS = "game_boost"
    private const val KEY_ACTIVE = "active"

    fun isActive(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun boost(context: Context): List<BoostAction> {
        val ctx = context.applicationContext
        val canWrite = AnimationScales.canWrite(ctx)
        val applied = canWrite && AnimationScales.apply(ctx, ScaleOption.HALF)
        val actions = listOf(
            BoostAction(
                R.string.gb_act_anim, applied,
                when {
                    applied -> null
                    !canWrite -> R.string.gb_reason_no_secure
                    else -> R.string.gb_reason_write_failed
                }
            ),
            BoostAction(R.string.gb_act_kill, false, R.string.gb_reason_android_blocks),
            BoostAction(R.string.gb_act_fps, false, R.string.gb_reason_no_fps)
        )
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, applied).apply()
        return actions
    }

    fun restore(context: Context): RestoreOutcome {
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!AnimationScales.hasBackup(ctx)) {
            prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
            return RestoreOutcome.NOTHING_TO_RESTORE
        }
        if (!AnimationScales.canWrite(ctx)) return RestoreOutcome.NO_PERMISSION
        val ok = AnimationScales.restore(ctx)
        if (ok) prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
        return if (ok) RestoreOutcome.RESTORED else RestoreOutcome.FAILED
    }
}
