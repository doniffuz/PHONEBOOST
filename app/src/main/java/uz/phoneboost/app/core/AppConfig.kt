package uz.phoneboost.app.core

import uz.phoneboost.app.BuildConfig

/**
 * Release sozlamalari. Qiymatlar kodda emas, gradle property orqali beriladi (~/.gradle/gradle.properties):
 *   PB_SUPPORT_URL (rasmiy Support sayti), PB_DONATION_URL, PB_CONTACT_EMAIL, PB_PRIVACY_POLICY_URL
 * Bo'sh bo'lsa tegishli tugma o'chiq turadi / "hali qo'shilmagan" deb yoziladi. Soxta havola ishlatilmaydi.
 *
 * TODO (Play Market oldidan): PB_PRIVACY_POLICY_URL ga HAQIQIY maxfiylik siyosati sahifasini bering.
 */
object AppConfig {
    val API_BASE: String get() = BuildConfig.API_BASE.trim().trimEnd('/')
    val SUPPORT_URL: String get() = BuildConfig.SUPPORT_URL
    val DONATION_URL: String get() = BuildConfig.DONATION_URL
    val CONTACT_EMAIL: String get() = BuildConfig.CONTACT_EMAIL
    val PRIVACY_POLICY_URL: String get() = BuildConfig.PRIVACY_POLICY_URL

    /** Top 10 ro'yxati shu fayldan o'qiladi (app/src/main/assets). */
    const val SUPPORTERS_ASSET: String = "supporters.json"
}
