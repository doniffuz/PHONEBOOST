package uz.phoneboost.app.domain

import uz.phoneboost.app.domain.model.DeviceStatus

const val SCAN_STEP_COUNT = 6

enum class SettingsAction { NONE, STORAGE, APPS }

enum class RecommendationType(val action: SettingsAction) {
    STORAGE_LOW(SettingsAction.STORAGE),
    MEMORY_HIGH(SettingsAction.APPS),
    TEMPERATURE_HIGH(SettingsAction.NONE),
    BATTERY_LOW(SettingsAction.NONE)
}

data class Recommendation(val type: RecommendationType)

/** Tavsiyalar faqat haqiqiy o‘lchovlardan chiqadi. */
object RecommendationEngine {

    fun analyze(status: DeviceStatus): List<Recommendation> {
        val result = mutableListOf<Recommendation>()

        val storage = status.freeStorageFraction
        if (storage != null && storage < 0.20f) result += Recommendation(RecommendationType.STORAGE_LOW)

        val ram = status.freeRamFraction
        if (ram != null && ram < 0.20f) result += Recommendation(RecommendationType.MEMORY_HIGH)

        val temp = status.batteryTempC
        if (temp != null && temp >= 40f) result += Recommendation(RecommendationType.TEMPERATURE_HIGH)

        val battery = status.batteryPercent
        if (battery != null && battery < 20 && status.isCharging != true) {
            result += Recommendation(RecommendationType.BATTERY_LOW)
        }
        return result
    }
}
