package uz.phoneboost.app.domain

import uz.phoneboost.app.domain.model.BatteryHealth
import uz.phoneboost.app.domain.model.DeviceStatus
import uz.phoneboost.app.domain.model.ThermalLevel

const val SCAN_STEP_COUNT = 7

enum class SettingsAction { NONE, STORAGE, APPS }

/** Ilova ichidagi sahifaga o‘tish (Android Settings emas). */
enum class AppTarget { NONE, STORAGE, BATTERY }

enum class RecommendationType(val action: SettingsAction, val target: AppTarget = AppTarget.NONE) {
    STORAGE_LOW(SettingsAction.STORAGE),
    LARGE_FILES(SettingsAction.NONE, AppTarget.STORAGE),
    MEMORY_HIGH(SettingsAction.APPS),
    TEMPERATURE_HIGH(SettingsAction.NONE),
    BATTERY_LOW(SettingsAction.NONE),
    BATTERY_HEALTH(SettingsAction.NONE, AppTarget.BATTERY)
}

data class Recommendation(val type: RecommendationType)

/** Tavsiyalar faqat haqiqiy o‘lchovlardan chiqadi. */
object RecommendationEngine {

    fun analyze(status: DeviceStatus): List<Recommendation> {
        val result = mutableListOf<Recommendation>()

        val storage = status.freeStorageFraction
        if (storage != null && storage < 0.20f) result += Recommendation(RecommendationType.STORAGE_LOW)
        if (storage != null && storage < 0.30f) result += Recommendation(RecommendationType.LARGE_FILES)

        val ram = status.freeRamFraction
        if ((ram != null && ram < 0.20f) || status.lowMemory == true) {
            result += Recommendation(RecommendationType.MEMORY_HIGH)
        }

        val temp = status.batteryTempC
        val hot = (temp != null && temp >= 40f) ||
            status.thermalLevel == ThermalLevel.SEVERE || status.thermalLevel == ThermalLevel.CRITICAL
        if (hot) result += Recommendation(RecommendationType.TEMPERATURE_HIGH)

        val battery = status.batteryPercent
        if (battery != null && battery < 20 && status.isCharging != true) {
            result += Recommendation(RecommendationType.BATTERY_LOW)
        }

        when (status.batteryHealth) {
            BatteryHealth.DEAD, BatteryHealth.FAILURE, BatteryHealth.OVER_VOLTAGE ->
                result += Recommendation(RecommendationType.BATTERY_HEALTH)
            else -> Unit
        }
        return result
    }
}
