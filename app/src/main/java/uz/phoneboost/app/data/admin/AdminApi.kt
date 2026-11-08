package uz.phoneboost.app.data.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Barcha maydonlar null bo‘lishi mumkin: backend bermagan ma’lumot uydirilmaydi. */
data class AdminStats(
    val users: Long?, val devices: Long?, val optimizations: Long?, val gameBoosts: Long?,
    val diagnostics: Long?, val donations: Long?, val errors: Long?, val appVersion: String?,
    val maintenance: Boolean?
)

/** Sessiya tokeni faqat xotirada (diskka yozilmaydi). Parol/secret APK ichida YO‘Q. */
data class AdminSession(val token: String)

interface AdminApi {
    suspend fun login(email: String, password: String): Result<AdminSession>
    suspend fun stats(session: AdminSession, days: Int): Result<AdminStats>
    suspend fun logs(session: AdminSession): Result<List<String>>
    suspend fun announce(session: AdminSession, text: String): Result<Unit>
    suspend fun setMaintenance(session: AdminSession, enabled: Boolean): Result<Unit>
}

/** Haqiqiy backend (HTTPS) uchun REST mijoz. Autentifikatsiya va avtorizatsiya SERVERDA bajariladi. */
class HttpAdminApi(private val baseUrl: String) : AdminApi {

    override suspend fun login(email: String, password: String): Result<AdminSession> = call(
        "POST", "/admin/login", null, JSONObject().put("email", email).put("password", password)
    ).mapCatching { AdminSession(it.getString("token")) }

    override suspend fun stats(session: AdminSession, days: Int): Result<AdminStats> =
        call("GET", "/admin/stats?days=$days", session.token, null).map { j ->
            fun l(k: String): Long? = if (j.has(k) && !j.isNull(k)) j.optLong(k) else null
            AdminStats(
                l("users"), l("devices"), l("optimizations"), l("gameBoosts"), l("diagnostics"),
                l("donations"), l("errors"),
                if (j.has("appVersion") && !j.isNull("appVersion")) j.optString("appVersion") else null,
                if (j.has("maintenance") && !j.isNull("maintenance")) j.optBoolean("maintenance") else null
            )
        }

    override suspend fun logs(session: AdminSession): Result<List<String>> =
        call("GET", "/admin/logs", session.token, null).map { j ->
            val arr: JSONArray = j.optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).map { arr.optString(it) }
        }

    override suspend fun announce(session: AdminSession, text: String): Result<Unit> =
        call("POST", "/admin/announcements", session.token, JSONObject().put("text", text)).map { }

    override suspend fun setMaintenance(session: AdminSession, enabled: Boolean): Result<Unit> =
        call("POST", "/admin/maintenance", session.token, JSONObject().put("enabled", enabled)).map { }

    private suspend fun call(method: String, path: String, token: String?, body: JSONObject?): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(baseUrl.startsWith("https://"))
                val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.connectTimeout = 10000
                c.readTimeout = 10000
                c.setRequestProperty("Accept", "application/json")
                if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
                try {
                    if (body != null) {
                        c.doOutput = true
                        c.setRequestProperty("Content-Type", "application/json")
                        c.outputStream.use { it.write(body.toString().toByteArray()) }
                    }
                    if (c.responseCode !in 200..299) error("http ${c.responseCode}")
                    val text = c.inputStream.bufferedReader().use { it.readText() }
                    if (text.isBlank()) JSONObject() else JSONObject(text)
                } finally {
                    c.disconnect()
                }
            }
        }
}
