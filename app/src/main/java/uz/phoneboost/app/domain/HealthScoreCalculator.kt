package uz.phoneboost.app.domain

import uz.phoneboost.app.domain.model.DeviceStatus

enum class HealthLevel { EXCELLENT, GOOD, ATTENTION, CHECK }

data class HealthResult(val score: Int, val level: HealthLevel)

/** Ball faqat haqiqiy o‘lchovlardan hisoblanadi. Ma’lumot bo‘lmasa, ball ko‘rsatilmaydi. */
object HealthScoreCalculator {

    fun calculate(status: DeviceStatus): HealthResult? {
        val storage = status.freeStorageFraction
        val ram = status.freeRamFraction
        val temp = status.batteryTempC
        val battery = status.batteryPercent

        if (storage == null && ram == null && temp == null && battery == null) return null

        var score = 100

        if (storage != null) {
            score -= when {
                storage < 0.10f -> 25
                storage < 0.20f -> 12
                else -> 0
            }
        }
        if (ram != null) {
            score -= when {
                ram < 0.10f -> 15
                ram < 0.20f -> 7
                else -> 0
            }
        }
        if (temp != null) {
            score -= when {
                temp >= 45f -> 25
                temp >= 40f -> 12
                else -> 0
            }
        }
        if (battery != null && battery < 15 && status.isCharging != true) score -= 5

        val value = score.coerceIn(0, 100)
        val level = when {
            value >= 85 -> HealthLevel.EXCELLENT
            value >= 70 -> HealthLevel.GOOD
            value >= 50 -> HealthLevel.ATTENTION
            else -> HealthLevel.CHECK
        }
        return HealthResult(value, level)
    }
}
