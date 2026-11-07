package uz.phoneboost.app.features.diagnostics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.ui.chargeStateText
import uz.phoneboost.app.core.ui.diagStatusColor
import uz.phoneboost.app.core.ui.diagStatusText
import uz.phoneboost.app.core.ui.formatBytes
import uz.phoneboost.app.core.ui.formatTemp
import uz.phoneboost.app.core.ui.hwStateText
import uz.phoneboost.app.core.ui.thermalText
import uz.phoneboost.app.domain.DiagKind
import uz.phoneboost.app.domain.DiagStatus
import uz.phoneboost.app.domain.DiagnosticsEngine
import uz.phoneboost.app.domain.model.DeviceInfo
import uz.phoneboost.app.domain.model.DeviceStatus
import uz.phoneboost.app.domain.model.SensorKind
import uz.phoneboost.app.features.device.DeviceInfoViewModel
import uz.phoneboost.app.features.home.HomeViewModel
import java.util.Locale

@StringRes
private fun DiagKind.title(): Int = when (this) {
    DiagKind.RAM -> R.string.report_card_ram
    DiagKind.STORAGE -> R.string.report_card_storage
    DiagKind.BATTERY -> R.string.stat_battery
    DiagKind.TEMPERATURE -> R.string.stat_temperature
    DiagKind.SENSORS -> R.string.row_sensors
    DiagKind.DISPLAY -> R.string.device_card_display
    DiagKind.BLUETOOTH -> R.string.row_bluetooth
    DiagKind.NFC -> R.string.row_nfc
    DiagKind.WIFI -> R.string.row_wifi
    DiagKind.SYSTEM -> R.string.diag_system
}

@Composable
fun DiagnosticsScreen(
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    deviceViewModel: DeviceInfoViewModel = viewModel()
) {
    OnResume {
        homeViewModel.refresh()
        deviceViewModel.reload()
    }
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    val info by deviceViewModel.info.collectAsStateWithLifecycle()
    val status = state.status
    val items = remember(status, info) { DiagnosticsEngine.run(status, info) }

    FeaturePage(R.string.feature_diagnostics, onBack) {
        val good = items.count { it.status == DiagStatus.GOOD }
        val attention = items.count { it.status == DiagStatus.ATTENTION }
        val check = items.count { it.status == DiagStatus.CHECK }
        SectionCard(R.string.diag_summary) {
            Text(
                stringResource(R.string.diag_summary_line, good, attention, check),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            NoteText(stringResource(R.string.diag_note), Modifier)
        }

        items.forEach { item ->
            SectionCard(item.kind.title()) {
                Text(
                    diagStatusText(item.status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = diagStatusColor(item.status)
                )
                Column { DiagDetails(item.kind, status, info) }
            }
        }
    }
}

@Composable
private fun DiagDetails(kind: DiagKind, s: DeviceStatus?, i: DeviceInfo?) {
    val context = LocalContext.current
    val noData = stringResource(R.string.no_data)
    when (kind) {
        DiagKind.RAM -> {
            InfoRow(stringResource(R.string.row_free), formatBytes(context, s?.ramAvailableBytes) ?: noData)
            InfoRow(stringResource(R.string.row_total), formatBytes(context, s?.ramTotalBytes) ?: noData)
            if (s?.lowMemory == true) NoteText(stringResource(R.string.diag_low_memory))
        }
        DiagKind.STORAGE -> {
            InfoRow(stringResource(R.string.row_free), formatBytes(context, s?.storageFreeBytes) ?: noData)
            InfoRow(stringResource(R.string.row_total), formatBytes(context, s?.storageTotalBytes) ?: noData)
        }
        DiagKind.BATTERY -> {
            InfoRow(stringResource(R.string.row_percent), s?.batteryPercent?.let { "$it%" } ?: noData)
            InfoRow(
                stringResource(R.string.row_status),
                s?.let { chargeStateText(it.chargeState) } ?: noData
            )
            val health = s?.let { uz.phoneboost.app.core.ui.batteryHealthText(it.batteryHealth) }
            if (health != null) {
                InfoRow(stringResource(R.string.row_health), health)
            } else {
                NoteText(stringResource(R.string.battery_health_unavailable))
            }
        }
        DiagKind.TEMPERATURE -> {
            InfoRow(stringResource(R.string.stat_temperature), formatTemp(s?.batteryTempC))
            val thermal = s?.let { thermalText(it.thermalLevel) }
            if (thermal != null) InfoRow(stringResource(R.string.row_thermal), thermal)
        }
        DiagKind.SENSORS -> {
            InfoRow(stringResource(R.string.row_sensor_count), i?.sensorCount?.toString() ?: noData)
            val kinds = i?.sensorKinds.orEmpty()
            SensorKind.entries.forEach { k ->
                val present = kinds[k]
                if (present != null) {
                    InfoRow(
                        stringResource(k.label()),
                        stringResource(if (present) R.string.sensor_yes else R.string.sensor_no)
                    )
                }
            }
        }
        DiagKind.DISPLAY -> {
            val w = i?.screenWidthPx
            val h = i?.screenHeightPx
            InfoRow(stringResource(R.string.row_resolution), if (w != null && h != null) "$w × $h" else noData)
            InfoRow(
                stringResource(R.string.row_refresh),
                i?.refreshRateHz?.let { String.format(Locale.getDefault(), "%.0f Hz", it) } ?: noData
            )
        }
        DiagKind.BLUETOOTH -> InfoRow(stringResource(R.string.row_status), i?.let { hwStateText(it.bluetooth) } ?: noData)
        DiagKind.NFC -> InfoRow(stringResource(R.string.row_status), i?.let { hwStateText(it.nfc) } ?: noData)
        DiagKind.WIFI -> {
            InfoRow(stringResource(R.string.row_status), i?.let { hwStateText(it.wifi) } ?: noData)
            val connected = i?.wifiConnected
            if (connected != null) {
                InfoRow(
                    stringResource(R.string.row_connection),
                    stringResource(if (connected) R.string.wifi_connected else R.string.wifi_not_connected)
                )
            }
        }
        DiagKind.SYSTEM -> {
            InfoRow(stringResource(R.string.row_android), i?.androidVersion ?: noData)
            InfoRow(stringResource(R.string.row_security_patch), i?.securityPatch ?: noData)
            val age = DiagnosticsEngine.patchAgeDays(i?.securityPatch)
            if (age != null && age > 365) NoteText(stringResource(R.string.diag_patch_old))
        }
    }
}

@StringRes
private fun SensorKind.label(): Int = when (this) {
    SensorKind.ACCELEROMETER -> R.string.sensor_accelerometer
    SensorKind.GYROSCOPE -> R.string.sensor_gyroscope
    SensorKind.MAGNETOMETER -> R.string.sensor_magnetometer
    SensorKind.PROXIMITY -> R.string.sensor_proximity
    SensorKind.LIGHT -> R.string.sensor_light
}
