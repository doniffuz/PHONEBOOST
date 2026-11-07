package uz.phoneboost.app.data.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.phoneboost.app.domain.model.BatteryHealth
import uz.phoneboost.app.domain.model.ChargeState
import uz.phoneboost.app.domain.model.DeviceStatus
import uz.phoneboost.app.domain.model.PowerSource
import uz.phoneboost.app.domain.model.ThermalLevel
import kotlin.math.abs

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
            Triple(info.totalMem, info.availMem, info.lowMemory)
        }.getOrNull()

        val currentMa = runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val raw = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            // Ko‘p qurilmalarda µA, ba’zilarida mA. Real tok kamida o‘nlab mA bo‘ladi.
            if (raw == null || raw == Int.MIN_VALUE || raw == 0) null
            else if (abs(raw) >= 10_000) abs(raw) / 1000 else abs(raw)
        }.getOrNull()

        val power = runCatching { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()
        val powerSave = runCatching { power?.isPowerSaveMode }.getOrNull()
        val thermal = runCatching {
            if (Build.VERSION.SDK_INT >= 29 && power != null) {
                when (power.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
                    PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
                    PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
                    PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.CRITICAL
                    else -> ThermalLevel.UNKNOWN
                }
            } else ThermalLevel.UNKNOWN
        }.getOrDefault(ThermalLevel.UNKNOWN)

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
        // EXTRA_CYCLE_COUNT (API 34). Qurilma bermasa -1 yoki 0 bo‘ladi.
        val cycles = battery?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }

        DeviceStatus(
            storageTotalBytes = storage?.first,
            storageFreeBytes = storage?.second,
            ramTotalBytes = ram?.first,
            ramAvailableBytes = ram?.second,
            lowMemory = ram?.third,
            batteryPercent = percent,
            batteryTempC = tempC,
            isCharging = charging,
            chargeState = chargeState,
            powerSource = powerSource,
            batteryHealth = health,
            batteryVoltageMv = voltage,
            batteryTechnology = technology,
            batteryCurrentMa = currentMa,
            batteryCycleCount = cycles,
            thermalLevel = thermal,
            powerSaveMode = powerSave
        )
    }
}
