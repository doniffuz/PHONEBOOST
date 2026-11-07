package uz.phoneboost.app.data.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.phoneboost.app.domain.model.DeviceStatus

/** Faqat ruxsat talab qilmaydigan, standart Android API'lar. */
class DeviceStatusRepository(private val context: Context) {

    suspend fun read(): DeviceStatus = withContext(Dispatchers.IO) {
        val storage = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.totalBytes to stat.availableBytes
        }.getOrNull()

        val ram = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem to info.availMem
        }.getOrNull()

        val battery: Intent? = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null

        val rawTemp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val tempC = if (rawTemp != null && rawTemp != Int.MIN_VALUE) rawTemp / 10f else null

        val rawStatus = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = if (rawStatus == -1) {
            null
        } else {
            rawStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                rawStatus == BatteryManager.BATTERY_STATUS_FULL
        }

        DeviceStatus(
            storageTotalBytes = storage?.first,
            storageFreeBytes = storage?.second,
            ramTotalBytes = ram?.first,
            ramAvailableBytes = ram?.second,
            batteryPercent = percent,
            batteryTempC = tempC,
            isCharging = charging
        )
    }
}
