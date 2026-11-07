package uz.phoneboost.app.domain

import uz.phoneboost.app.domain.model.BatteryHealth
import uz.phoneboost.app.domain.model.DeviceStatus
import uz.phoneboost.app.domain.model.ThermalLevel

enum class HealthLevel { EXCELLENT, GOOD, ATTENTION, CHECK }

enum class ScoreArea { STORAGE, RAM, BATTERY, TEMPERATURE, DEVICE }

/** Ballni qancha pasaytirgani (Hisobot sahifasida ko‘rsatiladi). */
data class ScorePenalty(val area: ScoreArea, val points: Int)

data class HealthResult(
    val score: Int,
    val level: HealthLevel,
    val penalties: List<ScorePenalty> = emptyList()
)

/**
 * Ball faqat haqiqiy o‘lchovlardan hisoblanadi. Ma’lumot bo‘lmasa, ball ko‘rsatilmaydi.
 * 90–100 Juda yaxshi, 75–89 Yaxshi, 50–74 E’tibor kerak, 0–49 Tekshirish kerak.
 */
object HealthScoreCalculator {

    fun levelFor(score: Int): HealthLevel = when {
        score >= 90 -> HealthLevel.EXCELLENT
        score >= 75 -> HealthLevel.GOOD
        score >= 50 -> HealthLevel.ATTENTION
        else -> HealthLevel.CHECK
    }

    fun calculate(status: DeviceStatus): HealthResult? {
        val storage = status.freeStorageFraction
        val ram = status.freeRamFraction
        val temp = status.batteryTempC
        val battery = status.batteryPercent

        val hasDeviceSignal = status.thermalLevel != ThermalLevel.UNKNOWN ||
            status.batteryHealth != BatteryHealth.UNKNOWN || status.lowMemory != null

        if (storage == null && ram == null && temp == null && battery == null && !hasDeviceSignal) return null

        val penalties = mutableListOf<ScorePenalty>()

        if (storage != null) {
            val p = when {
                storage < 0.10f -> 25
                storage < 0.15f -> 18
                storage < 0.20f -> 12
                storage < 0.30f -> 5
                else -> 0
            }
            if (p > 0) penalties += ScorePenalty(ScoreArea.STORAGE, p)
        }

        if (ram != null) {
            val p = when {
                ram < 0.10f -> 15
                ram < 0.15f -> 11
                ram < 0.20f -> 7
                else -> 0
            }
            if (p > 0) penalties += ScorePenalty(ScoreArea.RAM, p)
        }

        if (battery != null && status.isCharging != true) {
            val p = when {
                battery < 5 -> 10
                battery < 15 -> 5
                else -> 0
            }
            if (p > 0) penalties += ScorePenalty(ScoreArea.BATTERY, p)
        }

        if (temp != null) {
            val p = when {
                temp >= 45f -> 25
                temp >= 40f -> 12
                temp >= 38f -> 4
                else -> 0
            }
            if (p > 0) penalties += ScorePenalty(ScoreArea.TEMPERATURE, p)
        }

        var device = 0
        when (status.batteryHealth) {
            BatteryHealth.OVERHEAT, BatteryHealth.DEAD, BatteryHealth.OVER_VOLTAGE, BatteryHealth.FAILURE -> device += 15
            else -> Unit
        }
        when (status.thermalLevel) {
            ThermalLevel.SEVERE, ThermalLevel.CRITICAL -> device += 15
            ThermalLevel.MODERATE -> device += 6
            else -> Unit
        }
        if (status.lowMemory == true) device += 6
        device = device.coerceAtMost(25)
        if (device > 0) penalties += ScorePenalty(ScoreArea.DEVICE, device)

        val value = (100 - penalties.sumOf { it.points }).coerceIn(0, 100)
        return HealthResult(value, levelFor(value), penalties)
    }
}
