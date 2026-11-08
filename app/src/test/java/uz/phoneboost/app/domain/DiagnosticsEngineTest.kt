package uz.phoneboost.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.phoneboost.app.domain.model.DeviceStatus

class DiagnosticsEngineTest {

    private fun status(ramFree: Long, storageFree: Long, battery: Int, temp: Float) = DeviceStatus(
        storageTotalBytes = 100L, storageFreeBytes = storageFree,
        ramTotalBytes = 100L, ramAvailableBytes = ramFree,
        batteryPercent = battery, batteryTempC = temp, isCharging = false
    )

    private fun DiagnosticsEngineTest.statusOf(s: DeviceStatus, kind: DiagKind): DiagStatus =
        DiagnosticsEngine.run(s, null).first { it.kind == kind }.status

    @Test
    fun healthyDevice_isGood() {
        val s = status(ramFree = 50, storageFree = 60, battery = 80, temp = 30f)
        assertEquals(DiagStatus.GOOD, statusOf(s, DiagKind.RAM))
        assertEquals(DiagStatus.GOOD, statusOf(s, DiagKind.STORAGE))
        assertEquals(DiagStatus.GOOD, statusOf(s, DiagKind.BATTERY))
        assertEquals(DiagStatus.GOOD, statusOf(s, DiagKind.TEMPERATURE))
    }

    @Test
    fun stressedDevice_isFlagged() {
        val s = status(ramFree = 5, storageFree = 5, battery = 10, temp = 46f)
        assertEquals(DiagStatus.CHECK, statusOf(s, DiagKind.RAM))
        assertEquals(DiagStatus.CHECK, statusOf(s, DiagKind.STORAGE))
        assertEquals(DiagStatus.ATTENTION, statusOf(s, DiagKind.BATTERY))
        assertEquals(DiagStatus.CHECK, statusOf(s, DiagKind.TEMPERATURE))
    }

    @Test
    fun score_isLowerForStressedDevice() {
        val good = HealthScoreCalculator.calculate(status(50, 60, 80, 30f))
        val bad = HealthScoreCalculator.calculate(status(5, 5, 10, 46f))
        assertNotNull(good)
        assertNotNull(bad)
        assertTrue(good!!.score in 0..100)
        assertTrue(bad!!.score < good.score)
    }

    @Test
    fun missingData_isNotInvented() {
        val s = DeviceStatus(null, null, null, null, null, null, null)
        assertEquals(DiagStatus.CHECK, statusOf(s, DiagKind.RAM))
    }
}
