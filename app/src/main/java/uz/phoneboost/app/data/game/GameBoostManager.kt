package uz.phoneboost.app.data.game

import androidx.annotation.StringRes
import uz.phoneboost.app.R

data class BoostAction(@StringRes val title: Int, val done: Boolean, @StringRes val reason: Int? = null)

/**
 * O‘yin uchun tekshiruv va tavsiyalar. Ilova hech qanday tizim sozlamasini o‘zgartirmaydi, FPS/CPU/GPU/RAM ni “oshirmaydi”
 * va fon ilovalarini yopmaydi (Android bunga oddiy ilovaga ruxsat bermaydi). Ro‘yxatdagi har bir band halol “bajarilmadi” + sababi bilan ko‘rsatiladi.
 */
object GameBoostManager {
    fun advice(): List<BoostAction> = listOf(
        BoostAction(R.string.gb_act_anim, false, R.string.gb_reason_manual),
        BoostAction(R.string.gb_act_kill, false, R.string.gb_reason_android_blocks),
        BoostAction(R.string.gb_act_fps, false, R.string.gb_reason_no_fps)
    )
}
