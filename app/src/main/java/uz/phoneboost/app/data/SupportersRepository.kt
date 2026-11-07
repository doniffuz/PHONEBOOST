package uz.phoneboost.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import uz.phoneboost.app.core.AppConfig

data class Supporter(val name: String?, val amount: Long, val anonymous: Boolean)

/**
 * Top 10 ro‘yxati assets/supporters.json faylidan o‘qiladi (loyiha egasi o‘zi yuritadi).
 * Fayl bo‘sh bo‘lsa — ro‘yxat bo‘sh; hech qanday soxta ism ko‘rsatilmaydi.
 * Format: [{"name":"Ali","amount":50000,"anonymous":false}, ...]
 */
class SupportersRepository(private val context: Context) {

    suspend fun top10(): List<Supporter> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.assets.open(AppConfig.SUPPORTERS_ASSET).bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val list = ArrayList<Supporter>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val anonymous = o.optBoolean("anonymous", false)
                val name = o.optString("name", "").trim().ifEmpty { null }
                list += Supporter(
                    name = if (anonymous) null else name,
                    amount = o.optLong("amount", 0L),
                    anonymous = anonymous || name == null
                )
            }
            list.sortedByDescending { it.amount }.take(10)
        }.getOrDefault(emptyList())
    }
}
