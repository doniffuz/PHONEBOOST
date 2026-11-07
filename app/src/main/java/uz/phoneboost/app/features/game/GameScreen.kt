package uz.phoneboost.app.features.game

import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.PrimaryButton
import uz.phoneboost.app.core.ui.SecondaryButton
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.ui.chargeStateText
import uz.phoneboost.app.core.ui.formatBytes
import uz.phoneboost.app.core.ui.formatTemp
import uz.phoneboost.app.core.util.launchApp
import uz.phoneboost.app.core.util.openAppDetails
import uz.phoneboost.app.core.util.openBatteryOptimizationSettings
import uz.phoneboost.app.domain.model.ChargeState
import uz.phoneboost.app.features.home.HomeViewModel

private data class Game(@StringRes val name: Int, val packages: List<String>)

private val games = listOf(
    Game(R.string.game_pubg, listOf("com.tencent.ig", "com.pubg.krmobile", "com.vng.pubgmobile")),
    Game(R.string.game_freefire, listOf("com.dts.freefireth", "com.dts.freefiremax")),
    Game(R.string.game_cod, listOf("com.activision.callofduty.shooter")),
    Game(R.string.game_mlbb, listOf("com.mobile.legends")),
    Game(R.string.game_brawl, listOf("com.supercell.brawlstars")),
    Game(R.string.game_other, emptyList())
)

/**
 * Game Boost: HECH QANDAY soxta FPS/ping yo‘q. Faqat telefonning o‘yin oldidan REAL holati
 * (RAM, xotira, batareya, harorat, zaryad) va Android Game Mode mavjudligi ko‘rsatiladi.
 */
@Composable
fun GameScreen(homeViewModel: HomeViewModel, onBack: () -> Unit, onOpenApps: () -> Unit) {
    OnResume { homeViewModel.refresh() }

    val context = LocalContext.current
    val status = homeViewModel.state.collectAsStateWithLifecycle().value.status
    var selected by rememberSaveable { mutableStateOf(0) }
    val game = games[selected.coerceIn(0, games.lastIndex)]
    val noData = stringResource(R.string.no_data)
    val unavailable = stringResource(R.string.settings_unavailable)

    val installedPackage: String? = remember(selected) {
        game.packages.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
    }

    FeaturePage(R.string.feature_game, onBack) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            games.forEachIndexed { index, g ->
                FilterChip(
                    selected = index == selected,
                    onClick = { selected = index },
                    label = { Text(stringResource(g.name), maxLines = 1) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        SectionCard(game.name) {
            if (game.packages.isEmpty()) {
                NoteText(stringResource(R.string.game_other_hint))
            } else {
                Text(
                    stringResource(if (installedPackage != null) R.string.game_installed else R.string.game_not_installed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (installedPackage != null) BrandColors.Good else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Real o‘lchovlar
        SectionCard(R.string.game_device_state) {
            val ramFree = status?.ramAvailableBytes
            val ramTotal = status?.ramTotalBytes
            InfoRow(
                stringResource(R.string.report_card_ram),
                if (ramFree != null && ramTotal != null) {
                    stringResource(
                        R.string.of_free,
                        formatBytes(context, ramFree) ?: noData,
                        formatBytes(context, ramTotal) ?: noData
                    )
                } else noData
            )
            InfoRow(stringResource(R.string.report_card_storage), formatBytes(context, status?.storageFreeBytes)?.let {
                stringResource(R.string.free_value, it)
            } ?: noData)
            InfoRow(stringResource(R.string.stat_battery), status?.batteryPercent?.let { "$it%" } ?: noData)
            InfoRow(stringResource(R.string.stat_temperature), formatTemp(status?.batteryTempC))
            InfoRow(
                stringResource(R.string.row_charging),
                status?.let { chargeStateText(it.chargeState) } ?: noData
            )
            InfoRow(
                stringResource(R.string.row_power_save),
                when (status?.powerSaveMode) {
                    true -> stringResource(R.string.hw_on)
                    false -> stringResource(R.string.hw_off)
                    null -> noData
                }
            )
        }

        // Tayyorlik tekshiruvi (haqiqiy o‘lchovlardan)
        SectionCard(R.string.game_readiness) {
            val checks: List<Pair<Int, Boolean?>> = listOf(
                R.string.report_card_ram to status?.freeRamFraction?.let { it >= 0.20f },
                R.string.report_card_storage to status?.freeStorageFraction?.let { it >= 0.15f },
                R.string.stat_battery to status?.batteryPercent?.let { it >= 30 || status?.isCharging == true },
                R.string.stat_temperature to status?.batteryTempC?.let { it < 40f },
                R.string.row_power_save to status?.powerSaveMode?.let { !it }
            )
            checks.forEach { (label, ok) ->
                InfoRow(
                    stringResource(label),
                    when (ok) {
                        true -> "✓"
                        false -> "⚠"
                        null -> "?"
                    }
                )
            }
            val attention = checks.count { it.second == false }
            val unknownCount = checks.count { it.second == null }
            NoteText(
                when {
                    attention > 0 -> stringResource(R.string.game_ready_attention, attention)
                    unknownCount > 0 -> stringResource(R.string.game_ready_partial)
                    else -> stringResource(R.string.game_ready_ok)
                }
            )
            NoteText(stringResource(R.string.game_checks_legend))
        }

        // Android Game Mode
        SectionCard(R.string.game_mode_title) {
            NoteText(
                if (Build.VERSION.SDK_INT >= 31) stringResource(R.string.game_mode_supported, Build.VERSION.SDK_INT)
                else stringResource(R.string.game_mode_unsupported)
            )
            NoteText(stringResource(R.string.game_mode_dashboard))
        }

        NoteText(stringResource(R.string.game_no_fake))

        if (installedPackage != null) {
            PrimaryButton(stringResource(R.string.game_open), onClick = {
                if (!context.launchApp(installedPackage)) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
            })
            SecondaryButton(stringResource(R.string.game_app_settings), onClick = {
                if (!context.openAppDetails(installedPackage)) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
            })
        }
        if (game.packages.isEmpty()) {
            PrimaryButton(stringResource(R.string.game_pick_app), onClick = onOpenApps)
        }
        SecondaryButton(stringResource(R.string.game_battery_settings), onClick = {
            if (!context.openBatteryOptimizationSettings()) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
        })
    }
}
