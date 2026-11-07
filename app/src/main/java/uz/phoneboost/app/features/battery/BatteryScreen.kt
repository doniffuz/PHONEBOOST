package uz.phoneboost.app.features.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.ui.UsageBar
import uz.phoneboost.app.core.ui.batteryHealthText
import uz.phoneboost.app.core.ui.chargeStateText
import uz.phoneboost.app.core.ui.formatTemp
import uz.phoneboost.app.core.ui.powerSourceText
import uz.phoneboost.app.domain.model.BatteryHealth
import uz.phoneboost.app.domain.model.ChargeState
import uz.phoneboost.app.domain.model.PowerSource
import uz.phoneboost.app.features.home.HomeViewModel
import java.util.Locale

@Composable
fun BatteryScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = state.status
    val unknown = stringResource(R.string.unknown)
    val percent = status?.batteryPercent
    val health = batteryHealthText(status?.batteryHealth ?: BatteryHealth.UNKNOWN)
    val temp = status?.batteryTempC

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
                Text(
                    stringResource(R.string.feature_battery),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionCard(R.string.stat_battery) {
                    Text(
                        percent?.let { "$it%" } ?: unknown,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (percent != null) {
                        Spacer(Modifier.height(8.dp))
                        UsageBar(percent / 100f)
                    }
                }

                SectionCard(R.string.battery_details) {
                    InfoRow(
                        stringResource(R.string.row_status),
                        chargeStateText(status?.chargeState ?: ChargeState.UNKNOWN)
                    )
                    InfoRow(
                        stringResource(R.string.row_power),
                        powerSourceText(status?.powerSource ?: PowerSource.UNKNOWN)
                    )
                    InfoRow(stringResource(R.string.stat_temperature), formatTemp(temp))
                    InfoRow(
                        stringResource(R.string.row_voltage),
                        status?.batteryVoltageMv?.let {
                            String.format(Locale.getDefault(), "%.2f V", it / 1000f)
                        } ?: unknown
                    )
                    InfoRow(stringResource(R.string.row_tech), status?.batteryTechnology ?: unknown)
                    if (health != null) {
                        InfoRow(stringResource(R.string.row_health), health)
                    }
                }

                if (health == null) {
                    Text(
                        stringResource(R.string.battery_health_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (temp != null && temp >= 40f) {
                    Text(
                        stringResource(R.string.rec_temp_todo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandColors.Warn
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
