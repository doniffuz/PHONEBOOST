package uz.phoneboost.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateResult {
    data object NotConfigured : UpdateResult
    data object UpToDate : UpdateResult
    data class Available(val versionName: String, val versionCode: Int, val changelog: String, val url: String, val force: Boolean = false) : UpdateResult
    data object Error : UpdateResult
}

/**
 * Yangilanish haqida HAQIQIY ma’lumot: HTTPS JSON ({"versionCode":5,"versionName":"0.5.0","changelog":"...","url":"https://..."}).
 * Manzil PB_UPDATE_URL orqali beriladi. Berilmasa — “sozlanmagan”, soxta yangilanish ko‘rsatilmaydi.
 */
class UpdateRepository(private val configUrl: String) {

    suspend fun check(currentVersionCode: Int): UpdateResult {
        if (configUrl.isBlank() || !configUrl.startsWith("https://")) return UpdateResult.NotConfigured
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(configUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                try {
                    val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    val code = j.optInt("versionCode", -1)
                    if (code < 0) UpdateResult.Error
                    else if (code <= currentVersionCode) UpdateResult.UpToDate
                    else UpdateResult.Available(
                        j.optString("versionName"), code, j.optString("changelog"),
                        (j.optString("downloadUrl").ifBlank { j.optString("url") }).takeIf { it.startsWith("https://") } ?: "",
                        j.optBoolean("forceUpdate", false)
                    )
                } finally {
                    c.disconnect()
                }
            }.getOrDefault(UpdateResult.Error)
        }
    }
}
