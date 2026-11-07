package uz.phoneboost.app.domain.model

enum class ChargeState { CHARGING, DISCHARGING, NOT_CHARGING, FULL, UNKNOWN }
enum class PowerSource { BATTERY, AC, USB, WIRELESS, UNKNOWN }
enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD, UNKNOWN }

/** Android PowerManager termal holati (API 29+). */
enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, UNKNOWN }

/** Telefonning haqiqiy o‘lchangan holati. Null = ma’lumot mavjud emas. */
data class DeviceStatus(
    val storageTotalBytes: Long?,
    val storageFreeBytes: Long?,
    val ramTotalBytes: Long?,
    val ramAvailableBytes: Long?,
    val batteryPercent: Int?,
    val batteryTempC: Float?,
    val isCharging: Boolean?,
    val chargeState: ChargeState = ChargeState.UNKNOWN,
    val powerSource: PowerSource = PowerSource.UNKNOWN,
    val batteryHealth: BatteryHealth = BatteryHealth.UNKNOWN,
    val batteryVoltageMv: Int? = null,
    val batteryTechnology: String? = null,
    val batteryCurrentMa: Int? = null,
    val batteryCycleCount: Int? = null,
    val thermalLevel: ThermalLevel = ThermalLevel.UNKNOWN,
    val lowMemory: Boolean? = null,
    val powerSaveMode: Boolean? = null
) {
    val storageUsedBytes: Long?
        get() {
            val total = storageTotalBytes
            val free = storageFreeBytes
            return if (total != null && free != null) total - free else null
        }

    val ramUsedBytes: Long?
        get() {
            val total = ramTotalBytes
            val avail = ramAvailableBytes
            return if (total != null && avail != null) total - avail else null
        }

    val freeStorageFraction: Float?
        get() {
            val total = storageTotalBytes
            val free = storageFreeBytes
            return if (total != null && free != null && total > 0) free.toFloat() / total else null
        }

    val freeRamFraction: Float?
        get() {
            val total = ramTotalBytes
            val avail = ramAvailableBytes
            return if (total != null && avail != null && total > 0) avail.toFloat() / total else null
        }

    val busyRamPercent: Int?
        get() = freeRamFraction?.let { ((1f - it) * 100f).toInt().coerceIn(0, 100) }
}
