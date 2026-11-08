package uz.phoneboost.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uz.phoneboost.app.core.AppConfig
import java.net.HttpURLConnection
import java.net.URL

data class ApiResponse(val status: Int, val json: JSONObject?) {
    val ok: Boolean get() = status in 200..299
    val errorCode: String? get() = json?.optString("error")?.takeIf { it.isNotEmpty() }
}

/**
 * Backend'ga xavfsiz so'rov: faqat HTTPS, qisqa timeout. Hech qanday kalit/token yuborilmaydi (ochiq endpointlar),
 * so'rov/javob tanasi logga yozilmaydi. Tarmoq xatosi = null.
 */
object ApiClient {
    private const val TIMEOUT_MS = 8000
    private const val MAX_BYTES = 256 * 1024

    suspend fun get(path: String): ApiResponse? = request("GET", path, null)
    suspend fun post(path: String, body: JSONObject): ApiResponse? = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): ApiResponse? = withContext(Dispatchers.IO) {
        val base = AppConfig.API_BASE
        if (base.isEmpty()) return@withContext null
        runCatching {
            val c = URL(base + path).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.use { s -> String(s.readNBytes(MAX_BYTES), Charsets.UTF_8) }.orEmpty()
                ApiResponse(code, runCatching { JSONObject(text) }.getOrNull())
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
