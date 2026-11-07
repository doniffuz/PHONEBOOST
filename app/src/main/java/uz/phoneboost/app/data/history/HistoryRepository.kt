package uz.phoneboost.app.data.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import uz.phoneboost.app.domain.model.DeviceStatus
import java.io.File

/** Bitta tekshiruv natijasi. Null = o‘sha paytda ma’lumot mavjud bo‘lmagan. */
data class HistoryEntry(
    val timeMillis: Long,
    val score: Int?,
    val ramBusyPercent: Int?,
    val storageFreePercent: Int?,
    val batteryPercent: Int?,
    val batteryTempC: Float?,
    val source: String
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_AUTO = "auto"

        fun from(status: DeviceStatus, score: Int?, source: String) = HistoryEntry(
            timeMillis = System.currentTimeMillis(),
            score = score,
            ramBusyPercent = status.busyRamPercent,
            storageFreePercent = status.freeStorageFraction?.let { (it * 100f).toInt().coerceIn(0, 100) },
            batteryPercent = status.batteryPercent,
            batteryTempC = status.batteryTempC,
            source = source
        )
    }
}

/**
 * Tarix faqat shu telefonning ichki xotirasida (filesDir/history.json) saqlanadi, hech qayerga yuborilmaydi.
 * Funksiyalar bloklovchi: IO oqimidan chaqiring.
 */
class HistoryRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "history.json")

    fun load(): List<HistoryEntry> = synchronized(LOCK) { readLocked() }

    fun add(entry: HistoryEntry) = synchronized(LOCK) {
        val list = (listOf(entry) + readLocked()).take(MAX_ENTRIES)
        writeLocked(list)
    }

    fun clear() = synchronized(LOCK) { runCatching { file.delete() }; Unit }

    private fun readLocked(): List<HistoryEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HistoryEntry(
                timeMillis = o.optLong("t", 0L),
                score = o.optIntOrNull("s"),
                ramBusyPercent = o.optIntOrNull("r"),
                storageFreePercent = o.optIntOrNull("f"),
                batteryPercent = o.optIntOrNull("b"),
                batteryTempC = if (o.has("c") && !o.isNull("c")) o.optDouble("c").toFloat() else null,
                source = o.optString("src", HistoryEntry.SOURCE_MANUAL)
            )
        }
    }.getOrDefault(emptyList())

    private fun writeLocked(list: List<HistoryEntry>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(JSONObject().apply {
                    put("t", e.timeMillis)
                    e.score?.let { put("s", it) }
                    e.ramBusyPercent?.let { put("r", it) }
                    e.storageFreePercent?.let { put("f", it) }
                    e.batteryPercent?.let { put("b", it) }
                    e.batteryTempC?.let { put("c", it.toDouble()) }
                    put("src", e.source)
                })
            }
            val tmp = File(file.parentFile, "history.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) { file.writeText(arr.toString()); tmp.delete() }
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private companion object {
        val LOCK = Any()
        const val MAX_ENTRIES = 100
    }
}
