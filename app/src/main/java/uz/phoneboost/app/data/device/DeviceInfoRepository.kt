package uz.phoneboost.app.data.device

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import uz.phoneboost.app.domain.model.DeviceInfo
import uz.phoneboost.app.domain.model.HwState
import uz.phoneboost.app.domain.model.SensorKind

/** Statik qurilma ma’lumoti. Har bir qism alohida runCatching ichida: bittasi yiqilsa, qolganlari ishlaydi. */
class DeviceInfoRepository(private val context: Context) {

    fun read(): DeviceInfo {
        val pm = context.packageManager

        val sensorManager = runCatching { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }.getOrNull()
        val sensors: List<Sensor>? = runCatching { sensorManager?.getSensorList(Sensor.TYPE_ALL) }.getOrNull()
        fun has(type: Int): Boolean = runCatching { sensorManager?.getDefaultSensor(type) != null }.getOrDefault(false)
        val kinds: Map<SensorKind, Boolean> = if (sensorManager == null) emptyMap() else mapOf(
            SensorKind.ACCELEROMETER to has(Sensor.TYPE_ACCELEROMETER),
            SensorKind.GYROSCOPE to has(Sensor.TYPE_GYROSCOPE),
            SensorKind.MAGNETOMETER to has(Sensor.TYPE_MAGNETIC_FIELD),
            SensorKind.PROXIMITY to has(Sensor.TYPE_PROXIMITY),
            SensorKind.LIGHT to has(Sensor.TYPE_LIGHT)
        )

        val display = runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull()
        val mode = runCatching { display?.mode }.getOrNull()
        val w = mode?.physicalWidth
        val h = mode?.physicalHeight
        val refresh = runCatching { display?.refreshRate }.getOrNull()?.takeIf { it > 0f }

        val ramTotal = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem
        }.getOrNull()

        val storageTotal = runCatching { StatFs(Environment.getDataDirectory().path).totalBytes }.getOrNull()

        val cpu = buildString {
            val soc = if (Build.VERSION.SDK_INT >= 31) {
                listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                    .filter { it.isNotBlank() && !it.equals("unknown", true) }
                    .joinToString(" ")
            } else ""
            append(soc.ifBlank { Build.HARDWARE.orEmpty() })
        }.takeIf { it.isNotBlank() }

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() },
            model = Build.MODEL?.takeIf { it.isNotBlank() },
            androidVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() },
            sdk = Build.VERSION.SDK_INT,
            securityPatch = runCatching { Build.VERSION.SECURITY_PATCH }.getOrNull()?.takeIf { it.isNotBlank() },
            cpuName = cpu,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            ramTotalBytes = ramTotal,
            storageTotalBytes = storageTotal,
            screenWidthPx = w?.let { minOf(it, h ?: it) },
            screenHeightPx = h?.let { maxOf(it, w ?: it) },
            densityDpi = runCatching { context.resources.configuration.densityDpi }.getOrNull(),
            refreshRateHz = refresh,
            bluetooth = bluetoothState(pm),
            nfc = nfcState(pm),
            wifi = wifiState(pm),
            wifiConnected = wifiConnected(),
            sensorCount = sensors?.size,
            sensorNames = sensors.orEmpty().map { it.name }.distinct().sorted(),
            sensorKinds = kinds,
            uptimeMillis = SystemClock.elapsedRealtime()
        )
    }

    private fun bluetoothState(pm: PackageManager): HwState {
        if (!pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) return HwState.ABSENT
        return runCatching {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            when (adapter?.isEnabled) {
                true -> HwState.ON
                false -> HwState.OFF
                null -> HwState.PRESENT
            }
        }.getOrDefault(HwState.PRESENT)
    }

    private fun nfcState(pm: PackageManager): HwState {
        if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC)) return HwState.ABSENT
        return runCatching {
            val adapter = (context.getSystemService(Context.NFC_SERVICE) as? NfcManager)?.defaultAdapter
            when (adapter?.isEnabled) {
                true -> HwState.ON
                false -> HwState.OFF
                null -> HwState.PRESENT
            }
        }.getOrDefault(HwState.PRESENT)
    }

    private fun wifiState(pm: PackageManager): HwState {
        if (!pm.hasSystemFeature(PackageManager.FEATURE_WIFI)) return HwState.ABSENT
        return runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm == null) HwState.PRESENT else if (wm.isWifiEnabled) HwState.ON else HwState.OFF
        }.getOrDefault(HwState.PRESENT)
    }

    private fun wifiConnected(): Boolean? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@runCatching null
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrNull()
}
