package uz.phoneboost.app.domain.model

/** Uskunaning holati: ON/OFF (aniq bilinsa), PRESENT (bor, lekin holati ruxsatsiz bilinmaydi), ABSENT (yo‘q). */
enum class HwState { ON, OFF, PRESENT, ABSENT }

enum class SensorKind { ACCELEROMETER, GYROSCOPE, MAGNETOMETER, PROXIMITY, LIGHT }

/** Qurilma haqida statik ma’lumot. Null = Android bermadi. */
data class DeviceInfo(
    val manufacturer: String?,
    val model: String?,
    val androidVersion: String?,
    val sdk: Int,
    val securityPatch: String?,
    val cpuName: String?,
    val cpuCores: Int,
    val abis: List<String>,
    val ramTotalBytes: Long?,
    val storageTotalBytes: Long?,
    val screenWidthPx: Int?,
    val screenHeightPx: Int?,
    val densityDpi: Int?,
    val refreshRateHz: Float?,
    val bluetooth: HwState,
    val nfc: HwState,
    val wifi: HwState,
    val wifiConnected: Boolean?,
    val sensorCount: Int?,
    val sensorNames: List<String>,
    val sensorKinds: Map<SensorKind, Boolean>,
    val uptimeMillis: Long
)
