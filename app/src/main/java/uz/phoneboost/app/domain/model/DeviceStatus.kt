package uz.phoneboost.app.domain.model

/** Telefonning haqiqiy o‘lchangan holati. Null = ma’lumot mavjud emas. */
data class DeviceStatus(
    val storageTotalBytes: Long?,
    val storageFreeBytes: Long?,
    val ramTotalBytes: Long?,
    val ramAvailableBytes: Long?,
    val batteryPercent: Int?,
    val batteryTempC: Float?,
    val isCharging: Boolean?
) {
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
