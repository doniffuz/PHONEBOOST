package uz.phoneboost.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uz.phoneboost.app.core.AppConfig
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateResult {
    data object NotConfigured : UpdateResult
    data object UpToDate : UpdateResult
    data class Available(val versionName: String, val versionCode: Int, val changelog: String, val url: String, val force: Boolean = false) : UpdateResult
    data object Error : UpdateResult
}

/**
 * Yangilanish haqida HAQIQIY ma'lumot (HTTPS JSON). Manba: PB_UPDATE_URL, bo'lmasa backend (PB_API_BASE + /update).
 * Ikkalasi ham yo'q = "sozlanmagan". Minimal qo'llab-quvvatlanadigan versiyadan past ilova MAJBURIY yangilanadi.
 */
class UpdateRepository(configUrl: String) {
    private val url: String = configUrl.ifBlank { AppConfig.API_BASE.let { if (it.isEmpty()) "" else "$it/update" } }

    suspend fun check(currentVersionCode: Int): UpdateResult {
        if (url.isBlank() || !url.startsWith("https://")) return UpdateResult.NotConfigured
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                try {
                    parse(JSONObject(c.inputStream.bufferedReader().use { it.readText() }), currentVersionCode)
                } finally {
                    c.disconnect()
                }
            }.getOrDefault(UpdateResult.Error)
        }
    }

    internal fun parse(j: JSONObject, current: Int): UpdateResult {
        val code = j.optInt("versionCode", -1)
        if (code < 0) return UpdateResult.Error
        val d = UpdateLogic.decide(current, code, j.optInt("minSupportedVersionCode", 0), j.optBoolean("forceUpdate", false))
        if (!d.updateAvailable) return UpdateResult.UpToDate
        return UpdateResult.Available(
            j.optString("versionName"), code,
            j.optString("releaseNotes").ifBlank { j.optString("changelog") },
            (j.optString("downloadUrl").ifBlank { j.optString("url") }).takeIf { it.startsWith("https://") } ?: "",
            d.force
        )
    }
}
