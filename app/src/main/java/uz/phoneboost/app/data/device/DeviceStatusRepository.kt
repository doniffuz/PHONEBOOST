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
import uz.phoneboost.app.domain.model.BatteryHealth
import uz.phoneboost.app.domain.model.ChargeState
import uz.phoneboost.app.domain.model.DeviceStatus
import uz.phoneboost.app.domain.model.PowerSource

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
        val chargeState = when (rawStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeState.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.DISCHARGING
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeState.NOT_CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargeState.FULL
            else -> ChargeState.UNKNOWN
        }
        val charging = when (chargeState) {
            ChargeState.UNKNOWN -> null
            ChargeState.CHARGING, ChargeState.FULL -> true
            else -> false
        }

        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val powerSource = when {
            battery == null || plugged < 0 -> PowerSource.UNKNOWN
            plugged == 0 -> PowerSource.BATTERY
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> PowerSource.AC
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> PowerSource.USB
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> PowerSource.WIRELESS
            else -> PowerSource.UNKNOWN
        }

        val health = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            else -> BatteryHealth.UNKNOWN
        }

        val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val technology = battery?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }

        DeviceStatus(
            storageTotalBytes = storage?.first,
            storageFreeBytes = storage?.second,
            ramTotalBytes = ram?.first,
            ramAvailableBytes = ram?.second,
            batteryPercent = percent,
            batteryTempC = tempC,
            isCharging = charging,
            chargeState = chargeState,
            powerSource = powerSource,
            batteryHealth = health,
            batteryVoltageMv = voltage,
            batteryTechnology = technology
        )
    }
}
