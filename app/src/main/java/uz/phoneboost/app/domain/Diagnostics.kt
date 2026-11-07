package uz.phoneboost.app.domain

import uz.phoneboost.app.domain.model.BatteryHealth
import uz.phoneboost.app.domain.model.DeviceInfo
import uz.phoneboost.app.domain.model.DeviceStatus
import uz.phoneboost.app.domain.model.HwState
import uz.phoneboost.app.domain.model.SensorKind
import uz.phoneboost.app.domain.model.ThermalLevel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DiagKind { RAM, STORAGE, BATTERY, TEMPERATURE, SENSORS, DISPLAY, BLUETOOTH, NFC, WIFI, SYSTEM }

/** GOOD ✓ Yaxshi, ATTENTION ⚠ E’tibor, CHECK ? Tekshirish kerak, UNAVAILABLE = qurilmada bunday uskuna yo‘q. */
enum class DiagStatus { GOOD, ATTENTION, CHECK, UNAVAILABLE }

data class DiagItem(val kind: DiagKind, val status: DiagStatus)

/** Faqat o‘qiladigan real ma’lumotlardan holat chiqaradi. Virus skaneri EMAS. */
object DiagnosticsEngine {

    fun run(status: DeviceStatus?, info: DeviceInfo?): List<DiagItem> = listOf(
        DiagItem(DiagKind.RAM, ram(status)),
        DiagItem(DiagKind.STORAGE, storage(status)),
        DiagItem(DiagKind.BATTERY, battery(status)),
        DiagItem(DiagKind.TEMPERATURE, temperature(status)),
        DiagItem(DiagKind.SENSORS, sensors(info)),
        DiagItem(DiagKind.DISPLAY, display(info)),
        DiagItem(DiagKind.BLUETOOTH, hw(info?.bluetooth)),
        DiagItem(DiagKind.NFC, hw(info?.nfc)),
        DiagItem(DiagKind.WIFI, hw(info?.wifi)),
        DiagItem(DiagKind.SYSTEM, system(info))
    )

    private fun ram(s: DeviceStatus?): DiagStatus {
        val free = s?.freeRamFraction ?: return DiagStatus.CHECK
        return when {
            s.lowMemory == true || free < 0.10f -> DiagStatus.CHECK
            free < 0.20f -> DiagStatus.ATTENTION
            else -> DiagStatus.GOOD
        }
    }

    private fun storage(s: DeviceStatus?): DiagStatus {
        val free = s?.freeStorageFraction ?: return DiagStatus.CHECK
        return when {
            free < 0.10f -> DiagStatus.CHECK
            free < 0.20f -> DiagStatus.ATTENTION
            else -> DiagStatus.GOOD
        }
    }

    private fun battery(s: DeviceStatus?): DiagStatus {
        if (s == null || s.batteryPercent == null) return DiagStatus.CHECK
        return when {
            s.batteryHealth == BatteryHealth.DEAD ||
                s.batteryHealth == BatteryHealth.FAILURE ||
                s.batteryHealth == BatteryHealth.OVER_VOLTAGE -> DiagStatus.CHECK
            s.batteryHealth == BatteryHealth.OVERHEAT || s.batteryHealth == BatteryHealth.COLD -> DiagStatus.ATTENTION
            s.batteryPercent < 15 && s.isCharging != true -> DiagStatus.ATTENTION
            else -> DiagStatus.GOOD
        }
    }

    private fun temperature(s: DeviceStatus?): DiagStatus {
        val t = s?.batteryTempC ?: return DiagStatus.CHECK
        return when {
            t >= 45f || s.thermalLevel == ThermalLevel.SEVERE || s.thermalLevel == ThermalLevel.CRITICAL -> DiagStatus.CHECK
            t >= 40f || s.thermalLevel == ThermalLevel.MODERATE -> DiagStatus.ATTENTION
            else -> DiagStatus.GOOD
        }
    }

    private fun sensors(i: DeviceInfo?): DiagStatus {
        val count = i?.sensorCount ?: return DiagStatus.CHECK
        if (count == 0) return DiagStatus.CHECK
        return if (i.sensorKinds[SensorKind.ACCELEROMETER] == false) DiagStatus.ATTENTION else DiagStatus.GOOD
    }

    private fun display(i: DeviceInfo?): DiagStatus =
        if (i?.screenWidthPx != null && i.screenHeightPx != null) DiagStatus.GOOD else DiagStatus.CHECK

    private fun hw(state: HwState?): DiagStatus = when (state) {
        null -> DiagStatus.CHECK
        HwState.ABSENT -> DiagStatus.UNAVAILABLE
        else -> DiagStatus.GOOD
    }

    /** Xavfsizlik yangilanishi 1 yildan eski bo‘lsa — faqat “E’tibor” (qo‘rqitmasdan, faktni aytadi). */
    fun patchAgeDays(securityPatch: String?): Long? {
        if (securityPatch.isNullOrBlank()) return null
        return runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(securityPatch), LocalDate.now())
        }.getOrNull()
    }

    private fun system(i: DeviceInfo?): DiagStatus {
        if (i == null) return DiagStatus.CHECK
        val age = patchAgeDays(i.securityPatch)
        return if (age != null && age > 365) DiagStatus.ATTENTION else DiagStatus.GOOD
    }
}
