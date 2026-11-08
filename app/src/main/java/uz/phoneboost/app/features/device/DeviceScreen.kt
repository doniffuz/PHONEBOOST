package uz.phoneboost.app.features.device

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.ui.formatBytes
import uz.phoneboost.app.core.ui.hwStateText
import uz.phoneboost.app.features.home.HomeViewModel
import java.util.Locale

@Composable
fun DeviceScreen(
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    deviceViewModel: DeviceInfoViewModel = viewModel()
) {
    val context = LocalContext.current
    val info by deviceViewModel.info.collectAsStateWithLifecycle()
    val status = homeViewModel.state.collectAsStateWithLifecycle().value.status
    val noData = stringResource(R.string.no_data)

    FeaturePage(R.string.feature_phone, onBack) {
        val i = info
        if (i == null) {
            NoteText(stringResource(R.string.no_data_explained))
            return@FeaturePage
        }

        SectionCard(R.string.device_card_general) {
            InfoRow(stringResource(R.string.row_manufacturer), i.manufacturer ?: noData)
            InfoRow(stringResource(R.string.row_model), i.model ?: noData)
            InfoRow(stringResource(R.string.row_android), i.androidVersion ?: noData)
            InfoRow(stringResource(R.string.row_sdk), i.sdk.toString())
            InfoRow(stringResource(R.string.row_security_patch), i.securityPatch ?: noData)
        }

        SectionCard(R.string.device_card_hardware) {
            InfoRow(
                stringResource(R.string.row_cpu),
                (i.cpuName ?: noData) + " · " + stringResource(R.string.cpu_cores, i.cpuCores)
            )
            InfoRow(
                stringResource(R.string.row_abi),
                if (i.abis.isEmpty()) noData else i.abis.joinToString(", ")
            )
            InfoRow(stringResource(R.string.report_card_ram), formatBytes(context, i.ramTotalBytes) ?: noData)
            InfoRow(stringResource(R.string.report_card_storage), formatBytes(context, i.storageTotalBytes) ?: noData)
        }

        SectionCard(R.string.device_card_display) {
            val w = i.screenWidthPx
            val h = i.screenHeightPx
            InfoRow(
                stringResource(R.string.row_resolution),
                if (w != null && h != null) "$w × $h" else noData
            )
            InfoRow(
                stringResource(R.string.row_density),
                i.densityDpi?.let { "$it dpi" } ?: noData
            )
            InfoRow(
                stringResource(R.string.row_refresh),
                i.refreshRateHz?.let { String.format(Locale.getDefault(), "%.0f Hz", it) } ?: noData
            )
        }

        SectionCard(R.string.stat_battery) {
            InfoRow(
                stringResource(R.string.row_percent),
                status?.batteryPercent?.let { "$it%" } ?: noData
            )
            InfoRow(stringResource(R.string.row_tech), status?.batteryTechnology ?: noData)
        }

        SectionCard(R.string.device_card_connectivity) {
            InfoRow(stringResource(R.string.row_bluetooth), hwStateText(i.bluetooth))
            InfoRow(stringResource(R.string.row_nfc), hwStateText(i.nfc))
            InfoRow(stringResource(R.string.row_wifi), hwStateText(i.wifi))
        }

        SectionCard(R.string.row_sensors) {
            InfoRow(
                stringResource(R.string.row_sensor_count),
                i.sensorCount?.toString() ?: noData
            )
            if (i.sensorNames.isNotEmpty()) {
                NoteText(i.sensorNames.joinToString("\n"))
            }
        }

        SectionCard(R.string.row_uptime) {
            InfoRow(
                stringResource(R.string.row_uptime),
                DateUtils.formatElapsedTime(i.uptimeMillis / 1000).let { hms ->
                    val days = i.uptimeMillis / 86_400_000L
                    if (days > 0) stringResource(R.string.uptime_days, days.toInt(), hms) else hms
                }
            )
        }
    }
}
