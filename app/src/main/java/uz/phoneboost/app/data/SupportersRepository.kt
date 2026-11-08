package uz.phoneboost.app.data

import android.content.Context
import uz.phoneboost.app.data.remote.BackendRepository

data class Supporter(val name: String?, val amount: Long, val anonymous: Boolean)

/**
 * Top 10 backend'dan olinadi (faqat 'success' to'lovlar; real ism faqat donor roziligi bilan).
 * Backend sozlanmagan yoki ishlamasa — ro'yxat bo'sh; hech qanday soxta ism ko'rsatilmaydi.
 */
class SupportersRepository(@Suppress("unused") private val context: Context) {
    suspend fun top10(): List<Supporter> =
        BackendRepository.topDonors().map { Supporter(it.displayName, it.amount, it.anonymous) }
}
