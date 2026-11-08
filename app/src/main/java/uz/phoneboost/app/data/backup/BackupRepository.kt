package uz.phoneboost.app.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uz.phoneboost.app.data.automation.AutomationPrefs
import uz.phoneboost.app.data.history.HistoryRepository
import uz.phoneboost.app.data.settings.SettingsRepository
import uz.phoneboost.app.data.settings.ThemeMode

sealed interface RestoreResult {
    data class Ok(val historyCount: Int) : RestoreResult
    data object Invalid : RestoreResult
    data object TooLarge : RestoreResult
    data object Failed : RestoreResult
}

/**
 * Qo'lda zaxira/tiklash (foydalanuvchi tanlagan fayl, Storage Access Framework orqali).
 * FAQAT oq ro'yxat: tema, avtomatlashtirish yoqilgan/yo'qligi, tarix metadatasi.
 * Parol, token, OTP, biometrik va shaxsiy fayllar bu yerga hech qachon kirmaydi.
 */
class BackupRepository(context: Context) {
    private val app = context.applicationContext
    private val settings = SettingsRepository(app)
    private val history = HistoryRepository(app)
    private val automation = AutomationPrefs(app)

    suspend fun export(target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject().apply {
                put("app", "PHONEBOOST")
                put("format", FORMAT)
                put("createdAt", System.currentTimeMillis())
                put("theme", settings.themeMode.first().name)
                put("automationEnabled", automation.enabled)
                put("automationAlerts", automation.alerts)
                put("history", JSONArray(history.exportJson()))
            }
            app.contentResolver.openOutputStream(target, "wt")!!.use { it.write(root.toString().toByteArray()) }
            true
        }.getOrDefault(false)
    }

    suspend fun restore(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val bytes = app.contentResolver.openInputStream(source)?.use { it.readNBytes(MAX_BYTES + 1) }
                ?: return@withContext RestoreResult.Failed
            if (bytes.size > MAX_BYTES) return@withContext RestoreResult.TooLarge
            val root = JSONObject(String(bytes))
            if (root.optString("app") != "PHONEBOOST" || root.optInt("format", -1) != FORMAT) {
                return@withContext RestoreResult.Invalid
            }
            val theme = runCatching { ThemeMode.valueOf(root.optString("theme")) }.getOrNull()
            val arr = root.optJSONArray("history") ?: JSONArray()
            val count = history.importJson(arr.toString())
            theme?.let { settings.setThemeMode(it) }
            if (root.has("automationEnabled")) automation.enabled = root.optBoolean("automationEnabled", false)
            if (root.has("automationAlerts")) automation.alerts = root.optBoolean("automationAlerts", false)
            RestoreResult.Ok(count)
        } catch (e: org.json.JSONException) {
            RestoreResult.Invalid
        } catch (e: Exception) {
            RestoreResult.Failed
        }
    }

    private companion object {
        const val FORMAT = 1
        const val MAX_BYTES = 512 * 1024
    }
}
