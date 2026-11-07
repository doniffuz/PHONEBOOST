package uz.phoneboost.app.features.report

import android.os.Build
import android.text.format.Formatter
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import uz.phoneboost.app.core.ui.chargeStateText
import uz.phoneboost.app.core.ui.formatTemp
import uz.phoneboost.app.core.ui.healthColor
import uz.phoneboost.app.core.ui.healthLabelRes
import uz.phoneboost.app.core.ui.temperatureStatus
import uz.phoneboost.app.domain.model.ChargeState
import uz.phoneboost.app.features.home.HomeViewModel
import uz.phoneboost.app.features.home.RecommendationCard
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportScreen(viewModel: HomeViewModel) {
    LaunchedEffect(Unit) { viewModel.refresh() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val unknown = stringResource(R.string.unknown)
    val status = state.status

    fun bytes(value: Long?): String =
        if (value == null) unknown else Formatter.formatShortFileSize(context, value)

    val checkedAt = state.checkedAtMillis?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .format(Date(it))
    } ?: unknown

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.nav_report),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Umumiy holat
            SectionCard(R.string.report_card_health) {
                val result = state.result
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        result?.score?.toString() ?: "—",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.score_out_of),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(healthLabelRes(result?.level)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = healthColor(result?.level),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                InfoRow(stringResource(R.string.report_last_check), checkedAt)
            }

            // Saqlash joyi
            SectionCard(R.string.report_card_storage) {
                val total = status?.storageTotalBytes
                val used = status?.storageUsedBytes
                InfoRow(stringResource(R.string.row_total), bytes(total))
                InfoRow(stringResource(R.string.row_used), bytes(used))
                InfoRow(stringResource(R.string.row_free), bytes(status?.storageFreeBytes))
                if (total != null && used != null && total > 0) {
                    Spacer(Modifier.height(8.dp))
                    UsageBar(used.toFloat() / total)
                }
            }

            // RAM
            SectionCard(R.string.report_card_ram) {
                val total = status?.ramTotalBytes
                val used = status?.ramUsedBytes
                InfoRow(stringResource(R.string.row_total), bytes(total))
                InfoRow(
                    stringResource(R.string.row_used),
                    if (used == null) unknown else bytes(used) + status?.busyRamPercent?.let { " ($it%)" }.orEmpty()
                )
                InfoRow(stringResource(R.string.row_free), bytes(status?.ramAvailableBytes))
                if (total != null && used != null && total > 0) {
                    Spacer(Modifier.height(8.dp))
                    UsageBar(used.toFloat() / total)
                }
            }

            // Batareya
            SectionCard(R.string.stat_battery) {
                InfoRow(
                    stringResource(R.string.row_percent),
                    status?.batteryPercent?.let { "$it%" } ?: unknown
                )
                InfoRow(
                    stringResource(R.string.row_status),
                    chargeStateText(status?.chargeState ?: ChargeState.UNKNOWN)
                )
            }

            // Harorat
            SectionCard(R.string.stat_temperature) {
                InfoRow(stringResource(R.string.stat_temperature), formatTemp(status?.batteryTempC))
                InfoRow(stringResource(R.string.row_status), temperatureStatus(status?.batteryTempC))
            }

            // Qurilma
            SectionCard(R.string.report_card_device) {
                InfoRow(stringResource(R.string.row_manufacturer), Build.MANUFACTURER.ifBlank { unknown })
                InfoRow(stringResource(R.string.row_model), Build.MODEL.ifBlank { unknown })
                InfoRow(stringResource(R.string.row_android), Build.VERSION.RELEASE.ifBlank { unknown })
                InfoRow(stringResource(R.string.row_sdk), Build.VERSION.SDK_INT.toString())
            }

            // Tavsiyalar
            Text(
                stringResource(R.string.report_card_recs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (state.recommendations.isEmpty()) {
                Text(
                    stringResource(R.string.result_ok),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = BrandColors.Good
                )
            } else {
                state.recommendations.forEach { RecommendationCard(it) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
